package com.qingzhou.modules.execution.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.json.JsonPaths;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.dto.OpenApiExecuteResultVO;
import com.qingzhou.modules.execution.dto.RunResultVO;
import com.qingzhou.modules.execution.dto.RunStepVO;
import com.qingzhou.modules.execution.entity.ExecutionInstance;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.entity.WorkflowSnapshot;
import com.qingzhou.modules.workflow.service.WorkflowService;
import com.qingzhou.modules.workflow.service.WorkflowSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExecutionResultAssembler {

    private final ObjectMapper objectMapper;
    private final WorkflowSnapshotService workflowSnapshotService;
    private final WorkflowService workflowService;

    /** 编排试跑 / 立即触发：含步骤摘要，便于内部排障 */
    public RunResultVO from(ExecutionVO vo) {
        if (vo == null || vo.getInstance() == null) {
            return RunResultVO.builder()
                    .status("FAILED")
                    .errorMsg("无执行结果")
                    .steps(List.of())
                    .build();
        }
        ExecutionInstance instance = vo.getInstance();
        Object output = projectPublicOutput(vo);

        List<RunStepVO> steps = new ArrayList<>();
        for (ExecutionNodeLog log : vo.getLogs() == null ? List.<ExecutionNodeLog>of() : vo.getLogs()) {
            steps.add(RunStepVO.builder()
                    .nodeId(log.getNodeId())
                    .nodeName(log.getNodeName())
                    .componentCode(log.getComponentCode())
                    .status(log.getStatus())
                    .durationMs(log.getDurationMs())
                    .errorMsg(log.getErrorMsg())
                    .response(DbResultCleaner.stripRedundant(parseJson(log.getResponseBody())))
                    .build());
        }

        return RunResultVO.builder()
                .executionId(instance.getId())
                .executionNo(instance.getExecutionNo())
                .status(instance.getStatus())
                .durationMs(instance.getDurationMs())
                .errorMsg(instance.getErrorMsg())
                .output(output)
                .steps(steps)
                .build();
    }

    /** 开放网关：只返回调用结果，隐藏编排步骤与节点 ID */
    public OpenApiExecuteResultVO forOpenApi(ExecutionVO vo) {
        if (vo == null || vo.getInstance() == null) {
            return OpenApiExecuteResultVO.builder()
                    .status("FAILED")
                    .errorMsg("无执行结果")
                    .build();
        }
        ExecutionInstance instance = vo.getInstance();
        return OpenApiExecuteResultVO.builder()
                .executionId(instance.getId())
                .executionNo(instance.getExecutionNo())
                .status(instance.getStatus())
                .durationMs(instance.getDurationMs())
                .errorMsg(instance.getErrorMsg())
                .output(projectPublicOutput(vo))
                .build();
    }

    public Object projectPublicOutput(ExecutionVO vo) {
        Map<String, Object> schema = resolveOutputSchema(vo.getInstance());
        String mode = schema == null ? "" : String.valueOf(schema.getOrDefault("mode", "")).trim();
        // 无 schema / 未识别 mode：默认最后一步，避免历史 NULL 静默变多节点大包
        if (!StringUtils.hasText(mode) || "last".equalsIgnoreCase(mode)) {
            return lastStepOutput(vo);
        }
        if ("firstRow".equalsIgnoreCase(mode) || "first_row".equalsIgnoreCase(mode)) {
            return firstRowOutput(vo);
        }
        if ("merge".equalsIgnoreCase(mode)) {
            return mergeStepOutputs(vo);
        }
        if ("fields".equalsIgnoreCase(mode)) {
            String reshapeFrom = schema == null ? "" : stringVal(schema.get("reshapeFrom")).toLowerCase();
            if ("merge".equals(reshapeFrom) || !StringUtils.hasText(reshapeFrom)) {
                Object projected = projectFieldsFromMerge(vo, schema);
                if (projected != null) {
                    return projected;
                }
                Object rawFields = schema.get("fields");
                if (rawFields instanceof List<?> list && !list.isEmpty()) {
                    return new LinkedHashMap<>();
                }
                return mergeStepOutputs(vo);
            }
            Object projected = projectFields(vo, schema);
            if (projected != null) {
                return projected;
            }
            // 已配置 fields 但全部无效：返回空对象，禁止静默变成「最后一步」
            Object rawFields = schema.get("fields");
            if (rawFields instanceof List<?> list && !list.isEmpty()) {
                return new LinkedHashMap<>();
            }
            return lastStepOutput(vo);
        }
        if ("legacy".equalsIgnoreCase(mode)) {
            return buildLegacyPublicOutput(vo);
        }
        return lastStepOutput(vo);
    }

    /**
     * 按步骤名合并各步 stepOutputs 投影。
     * 无 stepOutputs 时默认 { result: 完整响应 }。
     */
    @SuppressWarnings("unchecked")
    private Object mergeStepOutputs(ExecutionVO vo) {
        Map<String, Object> byNodeId = resolveNodeOutputs(vo);
        Map<String, NodeIoMeta> metaById = resolveNodeIoMeta(vo.getInstance());
        List<ExecutionNodeLog> logs = vo.getLogs() == null ? List.of() : vo.getLogs();
        List<ExecutionNodeLog> ordered = logs.stream()
                .filter(log -> StringUtils.hasText(log.getNodeId()))
                .toList();
        if (ordered.isEmpty() && !byNodeId.isEmpty()) {
            // 无日志时按 outputResult 的 entry 顺序
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : byNodeId.entrySet()) {
                NodeIoMeta meta = metaById.getOrDefault(entry.getKey(), NodeIoMeta.fallback(entry.getKey()));
                String unique = uniqueStepName(result, meta.displayName);
                result.put(unique, projectStepPorts(entry.getValue(), meta.stepOutputs));
            }
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (ExecutionNodeLog log : ordered) {
            Object response = byNodeId.get(log.getNodeId());
            if (response == null) {
                response = DbResultCleaner.stripRedundant(parseJson(log.getResponseBody()));
            }
            NodeIoMeta meta = metaById.getOrDefault(log.getNodeId(), NodeIoMeta.fallback(
                    StringUtils.hasText(log.getNodeName()) ? log.getNodeName() : log.getNodeId()));
            String unique = uniqueStepName(result, meta.displayName);
            result.put(unique, projectStepPorts(response, meta.stepOutputs));
        }
        return result;
    }

    private Map<String, Object> projectStepPorts(Object response, List<Map<String, String>> stepOutputs) {
        Map<String, Object> ports = new LinkedHashMap<>();
        List<Map<String, String>> defs = (stepOutputs == null || stepOutputs.isEmpty())
                ? List.of(Map.of("key", "result", "fromPath", ""))
                : stepOutputs;
        for (Map<String, String> def : defs) {
            String key = stringVal(def.get("key"));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String fromPath = stringVal(def.get("fromPath"));
            Object value = StringUtils.hasText(fromPath) ? JsonPaths.get(response, fromPath) : response;
            ports.put(key, value);
        }
        return ports;
    }

    private String uniqueStepName(Map<String, Object> result, String name) {
        String base = StringUtils.hasText(name) ? name : "step";
        String unique = base;
        int i = 2;
        while (result.containsKey(unique)) {
            unique = base + "_" + i++;
        }
        return unique;
    }

    /** fields 投影，源为 merge 结果（fromNode=__merge__ 或 fromSource=merge） */
    @SuppressWarnings("unchecked")
    private Object projectFieldsFromMerge(ExecutionVO vo, Map<String, Object> schema) {
        Object rawFields = schema.get("fields");
        if (!(rawFields instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object merged = mergeStepOutputs(vo);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String key = stringVal(map.get("key"));
            String fromNode = stringVal(map.get("fromNode"));
            String fromPath = stringVal(map.get("fromPath"));
            String fromSource = stringVal(map.get("fromSource")).toLowerCase();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Object source = merged;
            // 兼容：fromNode 为步骤名且 fromSource 非 merge 时，从 merge[步骤名] 取
            if (!"merge".equals(fromSource) && !"__merge__".equals(fromNode)
                    && StringUtils.hasText(fromNode) && merged instanceof Map<?, ?> mergeMap
                    && mergeMap.containsKey(fromNode)) {
                source = mergeMap.get(fromNode);
            }
            Object value = StringUtils.hasText(fromPath) ? JsonPaths.get(source, fromPath) : source;
            result.put(key, value);
        }
        return result.isEmpty() ? null : result;
    }

    private Map<String, Object> resolveNodeOutputs(ExecutionVO vo) {
        Map<String, Object> byNodeId = DbResultCleaner.stripMap(parseObjectMap(vo.getInstance().getOutputResult()));
        if (byNodeId.isEmpty()) {
            for (ExecutionNodeLog log : vo.getLogs() == null ? List.<ExecutionNodeLog>of() : vo.getLogs()) {
                if (StringUtils.hasText(log.getNodeId())) {
                    byNodeId.put(log.getNodeId(), DbResultCleaner.stripRedundant(parseJson(log.getResponseBody())));
                }
            }
        }
        return byNodeId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, NodeIoMeta> resolveNodeIoMeta(ExecutionInstance instance) {
        Map<String, NodeIoMeta> result = new LinkedHashMap<>();
        String graphJson = null;
        if (instance.getSnapshotId() != null) {
            WorkflowSnapshot snapshot = workflowSnapshotService.getById(instance.getSnapshotId());
            if (snapshot != null) {
                graphJson = snapshot.getGraphJson();
            }
        }
        if (!StringUtils.hasText(graphJson) && instance.getWorkflowId() != null) {
            Workflow workflow = workflowService.getById(instance.getWorkflowId());
            if (workflow != null) {
                graphJson = workflow.getGraphJson();
            }
        }
        if (!StringUtils.hasText(graphJson)) {
            return result;
        }
        Object parsed = parseJson(graphJson);
        if (!(parsed instanceof Map<?, ?> graph)) {
            return result;
        }
        Object nodes = graph.get("nodes");
        if (!(nodes instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> node)) {
                continue;
            }
            String id = stringVal(node.get("id"));
            if (!StringUtils.hasText(id)) {
                continue;
            }
            Object dataObj = node.get("data");
            Map<String, Object> data = new LinkedHashMap<>();
            if (dataObj instanceof Map<?, ?> dataMap) {
                dataMap.forEach((k, v) -> data.put(String.valueOf(k), v));
            }
            String displayName = stringVal(data.get("componentName"));
            if (!StringUtils.hasText(displayName)) {
                displayName = stringVal(node.get("name"));
            }
            if (!StringUtils.hasText(displayName)) {
                displayName = id;
            }
            List<Map<String, String>> stepOutputs = parseStepOutputs(data.get("stepOutputs"));
            result.put(id, new NodeIoMeta(displayName, stepOutputs));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseStepOutputs(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String key = stringVal(map.get("key"));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String fromPath = stringVal(map.get("fromPath"));
            if ("$".equals(fromPath) || "*".equals(fromPath)) {
                fromPath = "";
            }
            rows.add(Map.of("key", key, "fromPath", fromPath));
        }
        return rows;
    }

    private record NodeIoMeta(String displayName, List<Map<String, String>> stepOutputs) {
        static NodeIoMeta fallback(String name) {
            return new NodeIoMeta(name, List.of());
        }
    }

    /**
     * 查询首行：末步若为 DB 结果（含 rows），返回 rows[0]；无行则 null；非 DB 形态回落完整末步结果。
     */
    @SuppressWarnings("unchecked")
    private Object firstRowOutput(ExecutionVO vo) {
        Object last = lastStepOutput(vo);
        if (!(last instanceof Map<?, ?> map)) {
            return last;
        }
        Object rows = map.get("rows");
        if (!(rows instanceof List<?> list)) {
            return last;
        }
        if (list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        return DbResultCleaner.stripRedundant(first);
    }

    private Object lastStepOutput(ExecutionVO vo) {
        List<ExecutionNodeLog> logs = vo.getLogs() == null ? List.of() : vo.getLogs();
        List<ExecutionNodeLog> successLogs = logs.stream()
                .filter(log -> "SUCCESS".equals(log.getStatus()))
                .toList();
        List<ExecutionNodeLog> source = successLogs.isEmpty() ? logs : successLogs;
        if (source.isEmpty()) {
            Map<String, Object> byNodeId = DbResultCleaner.stripMap(parseObjectMap(vo.getInstance().getOutputResult()));
            if (byNodeId.isEmpty()) {
                return null;
            }
            return byNodeId.values().stream().reduce((a, b) -> b).orElse(null);
        }
        ExecutionNodeLog last = source.get(source.size() - 1);
        Map<String, Object> byNodeId = DbResultCleaner.stripMap(parseObjectMap(vo.getInstance().getOutputResult()));
        if (StringUtils.hasText(last.getNodeId()) && byNodeId.containsKey(last.getNodeId())) {
            return byNodeId.get(last.getNodeId());
        }
        return DbResultCleaner.stripRedundant(parseJson(last.getResponseBody()));
    }

    @SuppressWarnings("unchecked")
    private Object projectFields(ExecutionVO vo, Map<String, Object> schema) {
        Object rawFields = schema.get("fields");
        if (!(rawFields instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Map<String, Object> byNodeId = resolveNodeOutputs(vo);
        Map<String, Object> requestByNodeId = new LinkedHashMap<>();
        for (ExecutionNodeLog log : vo.getLogs() == null ? List.<ExecutionNodeLog>of() : vo.getLogs()) {
            if (StringUtils.hasText(log.getNodeId())) {
                requestByNodeId.put(log.getNodeId(), DbResultCleaner.stripRedundant(parseJson(log.getRequestBody())));
            }
        }
        Map<String, Object> workflowInput = parseObjectMap(vo.getInstance().getInputParams());

        Map<String, Object> result = new LinkedHashMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String key = stringVal(map.get("key"));
            String fromNode = stringVal(map.get("fromNode"));
            String fromPath = stringVal(map.get("fromPath"));
            String fromSource = stringVal(map.get("fromSource")).toLowerCase();
            if (!StringUtils.hasText(key) || !StringUtils.hasText(fromNode)) {
                continue;
            }
            Object source;
            if ("input".equals(fromSource) || "__input__".equals(fromNode)) {
                source = workflowInput;
            } else if ("request".equals(fromSource)) {
                source = requestByNodeId.get(fromNode);
            } else if ("merge".equals(fromSource) || "__merge__".equals(fromNode)) {
                source = mergeStepOutputs(vo);
            } else {
                source = byNodeId.get(fromNode);
            }
            Object value = StringUtils.hasText(fromPath) ? JsonPaths.get(source, fromPath) : source;
            result.put(key, value);
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 开放网关优先用实例业务 output（DB 含 rows），避免节点日志里的 preview 截断视图。
     * 无 outputSchema 时保持历史行为。
     */
    private Object buildLegacyPublicOutput(ExecutionVO vo) {
        Map<String, Object> byNodeId = DbResultCleaner.stripMap(parseObjectMap(vo.getInstance().getOutputResult()));
        if (!byNodeId.isEmpty()) {
            if (byNodeId.size() == 1) {
                return byNodeId.values().iterator().next();
            }
            Map<String, String> idToName = new LinkedHashMap<>();
            for (ExecutionNodeLog log : vo.getLogs() == null ? List.<ExecutionNodeLog>of() : vo.getLogs()) {
                if (StringUtils.hasText(log.getNodeId())) {
                    idToName.put(log.getNodeId(),
                            StringUtils.hasText(log.getNodeName()) ? log.getNodeName() : log.getNodeId());
                }
            }
            Map<String, Object> byName = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : byNodeId.entrySet()) {
                String name = idToName.getOrDefault(entry.getKey(), entry.getKey());
                String unique = name;
                int i = 2;
                while (byName.containsKey(unique)) {
                    unique = name + "_" + i++;
                }
                byName.put(unique, entry.getValue());
            }
            return byName;
        }

        List<ExecutionNodeLog> logs = vo.getLogs() == null ? List.of() : vo.getLogs();
        List<ExecutionNodeLog> successLogs = logs.stream()
                .filter(log -> "SUCCESS".equals(log.getStatus()))
                .toList();
        List<ExecutionNodeLog> source = successLogs.isEmpty() ? logs : successLogs;
        if (source.isEmpty()) {
            return null;
        }
        if (source.size() == 1) {
            return DbResultCleaner.stripRedundant(parseJson(source.get(0).getResponseBody()));
        }
        Map<String, Object> byName = new LinkedHashMap<>();
        for (ExecutionNodeLog log : source) {
            String key = StringUtils.hasText(log.getNodeName()) ? log.getNodeName() : log.getNodeId();
            String unique = key;
            int i = 2;
            while (byName.containsKey(unique)) {
                unique = key + "_" + i++;
            }
            byName.put(unique, DbResultCleaner.stripRedundant(parseJson(log.getResponseBody())));
        }
        return byName;
    }

    private Map<String, Object> resolveOutputSchema(ExecutionInstance instance) {
        if (instance == null) {
            return null;
        }
        String json = null;
        if (instance.getSnapshotId() != null) {
            WorkflowSnapshot snapshot = workflowSnapshotService.getById(instance.getSnapshotId());
            if (snapshot != null) {
                json = snapshot.getOutputSchema();
            }
        }
        if (!StringUtils.hasText(json) && instance.getWorkflowId() != null) {
            Workflow workflow = workflowService.getById(instance.getWorkflowId());
            if (workflow != null) {
                json = workflow.getOutputSchema();
            }
        }
        if (!StringUtils.hasText(json)) {
            return null;
        }
        Object parsed = parseJson(json);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return null;
    }

    private Map<String, Object> parseObjectMap(String json) {
        Object parsed = parseJson(json);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Object parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return json;
        }
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
