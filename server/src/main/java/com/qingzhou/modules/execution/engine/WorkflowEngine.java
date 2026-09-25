package com.qingzhou.modules.execution.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.common.json.JsonPaths;
import com.qingzhou.common.json.Jsons;
import com.qingzhou.infra.wecom.TokenManager;
import com.qingzhou.modules.component.entity.ApiComponent;
import com.qingzhou.modules.component.service.ApiComponentService;
import com.qingzhou.modules.credential.entity.Credential;
import com.qingzhou.modules.credential.service.CredentialService;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.engine.sql.SqlParamBinder;
import com.qingzhou.modules.execution.entity.ExecutionInstance;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import com.qingzhou.modules.execution.service.ExecutionInstanceService;
import com.qingzhou.modules.execution.service.ExecutionNodeLogService;
import com.qingzhou.modules.execution.support.FailureCategory;
import com.qingzhou.modules.workflow.dto.DagGraph;
import com.qingzhou.modules.workflow.dto.DagNode;
import com.qingzhou.modules.workflow.dto.ParamMappingItem;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.entity.WorkflowSnapshot;
import com.qingzhou.modules.workflow.service.WorkflowService;
import com.qingzhou.modules.workflow.service.WorkflowSnapshotService;
import com.qingzhou.modules.workflow.support.DagScheduler;
import com.qingzhou.modules.workflow.support.DagValidator;
import com.qingzhou.modules.workflow.support.InputSchemaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private static final Set<String> META_KEYS = HttpUrlSupport.META_KEYS;
    private static final String INPUT_SOURCE_NODE = "__input__";
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WorkflowService workflowService;
    private final WorkflowSnapshotService workflowSnapshotService;
    private final ApiComponentService apiComponentService;
    private final ExecutionInstanceService executionInstanceService;
    private final ExecutionNodeLogService executionNodeLogService;
    private final NodeHttpInvoker nodeHttpInvoker;
    private final NodeJdbcInvoker nodeJdbcInvoker;
    private final OutboundAuthResolver outboundAuthResolver;
    private final TokenManager tokenManager;
    private final CredentialService credentialService;
    private final Jsons jsons;
    private final ObjectMapper objectMapper;

    public ExecutionVO tryRun(Long workflowId, Map<String, Object> input) {
        return run(workflowId, "TRY_RUN", null, input);
    }

    public ExecutionVO runByCode(String workflowCode, String triggerType, Long appId, Map<String, Object> input) {
        Workflow workflow = workflowService.lambdaQuery()
                .eq(Workflow::getWorkflowCode, workflowCode)
                .one();
        if (workflow == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工作流不存在: " + workflowCode);
        }
        return run(workflow, triggerType, appId, input);
    }

    public ExecutionVO run(Long workflowId, String triggerType, Long appId, Map<String, Object> input) {
        Workflow workflow = workflowService.getById(workflowId);
        if (workflow == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        return run(workflow, triggerType, appId, input, null);
    }

    public ExecutionVO run(Workflow workflow, String triggerType, Long appId, Map<String, Object> input) {
        return run(workflow, triggerType, appId, input, null);
    }

    public ExecutionVO replay(Long executionId, Map<String, Object> overrideInput) {
        ExecutionInstance origin = executionInstanceService.getById(executionId);
        if (origin == null) {
            throw new BizException(ResultCode.NOT_FOUND, "执行记录不存在");
        }
        if ("RUNNING".equals(origin.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "运行中的执行不能重放");
        }
        Workflow workflow = workflowService.getById(origin.getWorkflowId());
        if (workflow == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        Map<String, Object> input = (overrideInput != null && !overrideInput.isEmpty())
                ? overrideInput
                : parseInput(origin.getInputParams());
        return run(workflow, "REPLAY", origin.getTriggerAppId(), input, origin.getSnapshotId());
    }

    public ExecutionVO run(Workflow workflow, String triggerType, Long appId, Map<String, Object> input, Long preferredSnapshotId) {
        RunSpec spec = resolveRuntime(workflow, triggerType, preferredSnapshotId);
        Workflow runtime = spec.workflow();
        InputSchemaValidator.validate(runtime.getInputSchema(), input);
        DagGraph graph = jsons.fromJson(runtime.getGraphJson(), DagGraph.class);
        List<ParamMappingItem> mappings = parseMappings(runtime.getParamMapping());
        DagValidator.validate(graph, mappings);
        List<List<String>> levels = DagScheduler.levels(graph);
        Map<String, DagNode> nodeMap = new LinkedHashMap<>();
        for (DagNode node : graph.getNodes()) {
            nodeMap.put(node.getId(), node);
        }

        ExecutionInstance instance = startInstance(runtime, triggerType, appId, input, spec.snapshotId());
        Map<String, Object> outputs = new ConcurrentHashMap<>();
        Map<String, Object> requestPayloads = new ConcurrentHashMap<>();
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicReference<String> failMsg = new AtomicReference<>();
        LocalDateTime started = LocalDateTime.now();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (List<String> level : levels) {
                if (failed.get()) {
                    break;
                }
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String nodeId : level) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            Object output = executeNode(
                                    instance, runtime, nodeMap.get(nodeId), input, outputs, requestPayloads, mappings);
                            if (output != null) {
                                outputs.put(nodeId, output);
                            }
                        } catch (Exception ex) {
                            failed.set(true);
                            failMsg.compareAndSet(null, ex.getMessage());
                            log.warn("节点执行失败 nodeId={} msg={}", nodeId, ex.getMessage());
                        }
                    }, executor));
                }
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            }
        }

        LocalDateTime ended = LocalDateTime.now();
        instance.setEndTime(ended);
        instance.setDurationMs(java.time.Duration.between(started, ended).toMillis());
        if (failed.get()) {
            String msg = failMsg.get();
            boolean timedOut = FailureCategory.looksLikeTimeout(msg)
                    || executionNodeLogService.lambdaQuery()
                    .eq(ExecutionNodeLog::getExecutionId, instance.getId())
                    .eq(ExecutionNodeLog::getStatus, "TIMEOUT")
                    .exists();
            instance.setStatus(timedOut ? "TIMEOUT" : "FAILED");
            instance.setErrorMsg(msg);
        } else {
            instance.setStatus("SUCCESS");
            instance.setOutputResult(jsons.toJson(outputs));
        }
        executionInstanceService.updateById(instance);

        ExecutionVO vo = new ExecutionVO();
        vo.setInstance(executionInstanceService.getById(instance.getId()));
        vo.setLogs(executionNodeLogService.lambdaQuery()
                .eq(ExecutionNodeLog::getExecutionId, instance.getId())
                .orderByAsc(ExecutionNodeLog::getId)
                .list());
        return vo;
    }

    private Object executeNode(
            ExecutionInstance instance,
            Workflow workflow,
            DagNode dagNode,
            Map<String, Object> input,
            Map<String, Object> outputs,
            Map<String, Object> requestPayloads,
            List<ParamMappingItem> mappings) {
        ApiComponent component = resolveComponent(dagNode);
        Map<String, Object> payload = buildPayload(dagNode, input, outputs, requestPayloads, mappings);
        requestPayloads.put(dagNode.getId(), payload);
        ExecutionNodeLog nodeLog = new ExecutionNodeLog();
        nodeLog.setExecutionId(instance.getId());
        nodeLog.setNodeId(dagNode.getId());
        nodeLog.setNodeName(dagNode.getName() != null ? dagNode.getName() : component.getComponentName());
        nodeLog.setComponentId(component.getId());
        nodeLog.setComponentCode(component.getComponentCode());
        nodeLog.setRequestMethod(component.getHttpMethod());
        nodeLog.setStatus("RUNNING");
        nodeLog.setStartTime(LocalDateTime.now());
        executionNodeLogService.save(nodeLog);
        if (DatabaseComponentSupport.isDatabase(component)) {
            return executeDatabaseNode(nodeLog, component, payload);
        }
        return executeHttpNode(nodeLog, workflow, component, payload);
    }

    private Object executeDatabaseNode(ExecutionNodeLog nodeLog, ApiComponent component, Map<String, Object> payload) {
        DatabaseComponentSupport.DatabaseSpec spec;
        try {
            spec = DatabaseComponentSupport.spec(component, jsons);
        } catch (BizException ex) {
            failLog(nodeLog, "FAILED", ex.getMessage());
            throw ex;
        }
        nodeLog.setRequestMethod(spec.method());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sql", spec.sql());
        request.put("params", SqlParamBinder.summary(spec.bound(), payload));
        request.put("accessMode", spec.accessMode());
        nodeLog.setRequestBody(jsons.toJson(request));
        nodeLog.setRequestHeaders(jsons.toJson(Map.of("datasourceId", spec.datasourceId(), "maxRows", spec.maxRows())));
        executionNodeLogService.updateById(nodeLog);

        int timeout = component.getTimeoutMs() == null ? 10000 : component.getTimeoutMs();
        int retryTimes = component.getRetryTimes() == null ? 0 : component.getRetryTimes();
        if ("UPDATE".equals(spec.method())) {
            retryTimes = 0;
        }
        DbCallResult last = null;
        int attempted = 0;
        for (int i = 0; i <= retryTimes; i++) {
            attempted = i;
            last = nodeJdbcInvoker.invoke(spec, payload, timeout);
            if (last.success()) {
                break;
            }
            boolean retryable = last.timeout();
            if (!retryable || i == retryTimes) {
                break;
            }
            try {
                Thread.sleep(component.getRetryIntervalMs() == null ? 1000 : component.getRetryIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        nodeLog.setEndTime(LocalDateTime.now());
        nodeLog.setDurationMs(java.time.Duration.between(nodeLog.getStartTime(), nodeLog.getEndTime()).toMillis());
        nodeLog.setRetryCount(attempted);
        if (last != null) {
            nodeLog.setRequestUrl(last.displayUrl());
            nodeLog.setResponseStatus(last.success() ? 200 : (last.timeout() ? 504 : 400));
            nodeLog.setResponseBody(jsons.toJson(last.logBody() == null || last.logBody().isEmpty()
                    ? last.output() : last.logBody()));
        }
        if (last != null && last.success()) {
            nodeLog.setStatus("SUCCESS");
            executionNodeLogService.updateById(nodeLog);
            return last.output() == null ? Map.of() : last.output();
        }
        nodeLog.setStatus(last != null && last.timeout() ? "TIMEOUT" : "FAILED");
        nodeLog.setErrorMsg(last == null ? "无响应" : last.error());
        executionNodeLogService.updateById(nodeLog);
        throw new BizException(ResultCode.THIRD_PARTY_ERROR,
                "节点 " + nodeLog.getNodeName() + " 失败: " + nodeLog.getErrorMsg());
    }

    private Object executeHttpNode(
            ExecutionNodeLog nodeLog,
            Workflow workflow,
            ApiComponent component,
            Map<String, Object> payload) {

        String token;
        try {
            token = resolveToken(workflow, component);
        } catch (BizException ex) {
            failLog(nodeLog, "FAILED", ex.getMessage());
            throw ex;
        }
        String rawUrl = HttpUrlSupport.renderUrl(component.getUrlTemplate(), payload, token);
        URI uri = HttpUrlSupport.buildUri(rawUrl, component.getHttpMethod(), payload);
        OutboundAuthResolver.ResolvedAuth auth;
        try {
            auth = outboundAuthResolver.resolve(component, component.getHttpMethod(), uri);
        } catch (BizException ex) {
            failLog(nodeLog, "FAILED", ex.getMessage());
            throw ex;
        }
        uri = HttpAuthSupport.appendQueryParams(uri, auth.queryParams());
        String body = jsons.toJson(payload);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.putAll(auth.headers());
        nodeLog.setRequestUrl(HttpUrlSupport.maskSecret(uri.toString()));
        nodeLog.setRequestHeaders(jsons.toJson(HttpAuthSupport.maskHeaders(headers)));
        nodeLog.setRequestBody(body);
        executionNodeLogService.updateById(nodeLog);

        int timeout = component.getTimeoutMs() == null ? 10000 : component.getTimeoutMs();
        int retryTimes = component.getRetryTimes() == null ? 0 : component.getRetryTimes();
        int retryInterval = component.getRetryIntervalMs() == null ? 1000 : component.getRetryIntervalMs();

        HttpCallResult last = null;
        int attempted = 0;
        for (int i = 0; i <= retryTimes; i++) {
            attempted = i;
            last = nodeHttpInvoker.invoke(component.getHttpMethod(), uri, headers, body, timeout, auth);
            if (last.success()) {
                break;
            }
            boolean retryable = last.timeout() || last.status() >= 500 || last.status() == 429;
            if (!retryable || i == retryTimes) {
                break;
            }
            try {
                Thread.sleep(retryInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        nodeLog.setEndTime(LocalDateTime.now());
        nodeLog.setDurationMs(java.time.Duration.between(nodeLog.getStartTime(), nodeLog.getEndTime()).toMillis());
        nodeLog.setRetryCount(attempted);
        if (last != null) {
            nodeLog.setResponseStatus(last.status() == 0 ? null : last.status());
            nodeLog.setResponseBody(last.body());
        }
        if (last != null && last.success()) {
            nodeLog.setStatus("SUCCESS");
            executionNodeLogService.updateById(nodeLog);
            return parseOutput(last.body());
        }
        nodeLog.setStatus(last != null && last.timeout() ? "TIMEOUT" : "FAILED");
        nodeLog.setErrorMsg(last == null ? "无响应" : (last.error() != null ? last.error() : "HTTP " + last.status()));
        executionNodeLogService.updateById(nodeLog);
        throw new BizException(ResultCode.THIRD_PARTY_ERROR,
                "节点 " + nodeLog.getNodeName() + " 失败: " + nodeLog.getErrorMsg());
    }

    private void failLog(ExecutionNodeLog nodeLog, String status, String errorMsg) {
        nodeLog.setStatus(status);
        nodeLog.setErrorMsg(errorMsg);
        nodeLog.setEndTime(LocalDateTime.now());
        if (nodeLog.getStartTime() != null) {
            nodeLog.setDurationMs(java.time.Duration.between(nodeLog.getStartTime(), nodeLog.getEndTime()).toMillis());
        }
        executionNodeLogService.updateById(nodeLog);
    }

    private ApiComponent resolveComponent(DagNode dagNode) {
        ApiComponent component = null;
        if (dagNode.getComponentId() != null) {
            component = apiComponentService.getById(dagNode.getComponentId());
        }
        if (component == null && StringUtils.hasText(dagNode.getComponentCode())) {
            component = apiComponentService.lambdaQuery()
                    .eq(ApiComponent::getComponentCode, dagNode.getComponentCode())
                    .one();
        }
        if (component == null && dagNode.getData() != null && dagNode.getData().get("componentId") != null) {
            component = apiComponentService.getById(Long.valueOf(String.valueOf(dagNode.getData().get("componentId"))));
        }
        if (component == null) {
            throw new BizException(ResultCode.NOT_FOUND, "节点未绑定接口组件: " + dagNode.getId());
        }
        return component;
    }

    private Map<String, Object> buildPayload(
            DagNode dagNode,
            Map<String, Object> input,
            Map<String, Object> outputs,
            Map<String, Object> requestPayloads,
            List<ParamMappingItem> mappings) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (input != null) {
            payload.putAll(input);
        }
        if (dagNode.getData() != null) {
            dagNode.getData().forEach((k, v) -> {
                if (!META_KEYS.contains(k)) {
                    payload.put(k, v);
                }
            });
        }
        if (mappings != null) {
            for (ParamMappingItem mapping : mappings) {
                if (!dagNode.getId().equals(mapping.getToNode())) {
                    continue;
                }
                Object value = resolveMappingValue(mapping, input, outputs, requestPayloads);
                JsonPaths.put(payload, mapping.getToPath(), value);
            }
        }
        return payload;
    }

    private Object resolveMappingValue(
            ParamMappingItem mapping,
            Map<String, Object> input,
            Map<String, Object> outputs,
            Map<String, Object> requestPayloads) {
        String fromSource = mapping.getFromSource() == null ? "" : mapping.getFromSource().trim();
        if (INPUT_SOURCE_NODE.equals(mapping.getFromNode()) || "input".equalsIgnoreCase(fromSource)) {
            return JsonPaths.get(input, mapping.getFromPath());
        }
        if ("request".equalsIgnoreCase(fromSource)) {
            Object source = requestPayloads == null ? null : requestPayloads.get(mapping.getFromNode());
            return JsonPaths.get(source, mapping.getFromPath());
        }
        Object source = outputs.get(mapping.getFromNode());
        return JsonPaths.get(source, mapping.getFromPath());
    }

    private String resolveToken(Workflow workflow, ApiComponent component) {
        HttpAuthSupport.AuthResult inline = HttpAuthSupport.resolve(component);
        Map<String, Object> config = HttpAuthSupport.readConfigPublic(component.getExtraConfig());
        Map<String, Object> auth = HttpAuthSupport.asMapPublic(config.get("auth"));
        String authType = HttpAuthSupport.stringValuePublic(auth.get("type"), "none");
        Long authCredentialId = null;
        Object rawId = auth.get("credentialId");
        if (rawId instanceof Number number) {
            authCredentialId = number.longValue();
        } else if (rawId != null && StringUtils.hasText(String.valueOf(rawId))) {
            try {
                authCredentialId = Long.parseLong(String.valueOf(rawId).trim());
            } catch (NumberFormatException ignored) {
                authCredentialId = null;
            }
        }

        boolean needsWecom = HttpUrlSupport.needsAccessToken(component) || inline.needsWecomToken()
                || "wecom".equalsIgnoreCase(authType);
        if ("credential".equalsIgnoreCase(authType) && authCredentialId != null) {
            Credential credential = credentialService.getById(authCredentialId);
            if (credential != null && "WECOM".equalsIgnoreCase(credential.getCredentialType())) {
                return tokenManager.getAccessToken(authCredentialId);
            }
            // HTTP_AUTH 等不在此换企微 Token
            if (!HttpUrlSupport.needsAccessToken(component)) {
                return null;
            }
        }
        if (!needsWecom) {
            return null;
        }
        try {
            if ("INDEPENDENT".equalsIgnoreCase(workflow.getCredentialMode()) && workflow.getCredentialId() != null) {
                return tokenManager.getAccessToken(workflow.getCredentialId());
            }
            return tokenManager.getAccessTokenForWorkflow(workflow.getId());
        } catch (BizException ex) {
            throw ex;
        }
    }

    private Object parseOutput(String body) {
        if (!StringUtils.hasText(body)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of("raw", body);
        }
    }

    private record RunSpec(Workflow workflow, Long snapshotId) {
    }

    /**
     * 试运行走画布草稿；调度 / OpenAPI 绑定最近一次发布快照，避免编排中途改图影响在途任务。
     * 重放优先使用原单快照，快照不在则回落草稿。
     */
    private RunSpec resolveRuntime(Workflow workflow, String triggerType, Long preferredSnapshotId) {
        if ("REPLAY".equals(triggerType)) {
            if (preferredSnapshotId != null) {
                WorkflowSnapshot snapshot = workflowSnapshotService.getById(preferredSnapshotId);
                if (snapshot != null && workflow.getId().equals(snapshot.getWorkflowId())) {
                    return new RunSpec(overlay(workflow, snapshot), snapshot.getId());
                }
            }
            if (!StringUtils.hasText(workflow.getGraphJson())) {
                throw new BizException(ResultCode.BAD_REQUEST, "无法重放：工作流没有可用编排");
            }
            return new RunSpec(workflow, null);
        }
        if ("TRY_RUN".equals(triggerType) || "MANUAL".equals(triggerType)) {
            if (!StringUtils.hasText(workflow.getGraphJson())) {
                throw new BizException(ResultCode.BAD_REQUEST, "工作流尚未编排");
            }
            return new RunSpec(workflow, null);
        }
        if ("DISABLED".equals(workflow.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "工作流已停用");
        }
        WorkflowSnapshot snapshot = workflowSnapshotService.lambdaQuery()
                .eq(WorkflowSnapshot::getWorkflowId, workflow.getId())
                .orderByDesc(WorkflowSnapshot::getVersion)
                .last("LIMIT 1")
                .one();
        if (snapshot == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先发布工作流后再通过调度或 OpenAPI 执行");
        }
        return new RunSpec(overlay(workflow, snapshot), snapshot.getId());
    }

    private Workflow overlay(Workflow src, WorkflowSnapshot snapshot) {
        Workflow runtime = new Workflow();
        runtime.setId(src.getId());
        runtime.setWorkflowCode(src.getWorkflowCode());
        runtime.setWorkflowName(src.getWorkflowName());
        runtime.setStatus(src.getStatus());
        runtime.setVersion(snapshot.getVersion());
        runtime.setGraphJson(snapshot.getGraphJson());
        runtime.setParamMapping(snapshot.getParamMapping());
        runtime.setInputSchema(snapshot.getInputSchema());
        runtime.setOutputSchema(snapshot.getOutputSchema());
        runtime.setCredentialMode(snapshot.getCredentialMode());
        runtime.setCredentialId(snapshot.getCredentialId());
        runtime.setTimeoutMs(snapshot.getTimeoutMs());
        return runtime;
    }

    private ExecutionInstance startInstance(
            Workflow workflow, String triggerType, Long appId, Map<String, Object> input, Long snapshotId) {
        ExecutionInstance instance = new ExecutionInstance();
        String prefix = "REPLAY".equals(triggerType) ? "RP" : "TR";
        instance.setExecutionNo(prefix + LocalDateTime.now().format(NO_FMT) + UUID.randomUUID().toString().substring(0, 4));
        instance.setWorkflowId(workflow.getId());
        instance.setWorkflowVersion(workflow.getVersion() == null ? 1 : workflow.getVersion());
        instance.setSnapshotId(snapshotId);
        instance.setTriggerType(triggerType);
        instance.setTriggerAppId(appId);
        instance.setStatus("RUNNING");
        instance.setInputParams(jsons.toJson(input));
        instance.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        instance.setStartTime(LocalDateTime.now());
        executionInstanceService.save(instance);
        return instance;
    }

    private Map<String, Object> parseInput(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private List<ParamMappingItem> parseMappings(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

}
