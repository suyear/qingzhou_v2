package com.qingzhou.modules.lineage.service.impl;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.component.entity.ApiComponent;
import com.qingzhou.modules.component.service.ApiComponentService;
import com.qingzhou.modules.execution.entity.ExecutionInstance;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import com.qingzhou.modules.execution.service.ExecutionInstanceService;
import com.qingzhou.modules.execution.service.ExecutionNodeLogService;
import com.qingzhou.modules.lineage.dto.ExecutionChainVO;
import com.qingzhou.modules.lineage.dto.LineageRefVO;
import com.qingzhou.modules.lineage.dto.LineageVO;
import com.qingzhou.modules.lineage.dto.TriggerSourceVO;
import com.qingzhou.modules.lineage.service.LineageService;
import com.qingzhou.modules.lineage.support.LineageSupport;
import com.qingzhou.modules.openapi.entity.OpenapiApp;
import com.qingzhou.modules.openapi.entity.OpenapiAppWorkflow;
import com.qingzhou.modules.openapi.service.OpenapiAppService;
import com.qingzhou.modules.openapi.service.OpenapiAppWorkflowService;
import com.qingzhou.modules.schedule.entity.ScheduleJob;
import com.qingzhou.modules.schedule.service.ScheduleJobService;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LineageServiceImpl implements LineageService {

    private static final Map<String, String> TRIGGER_LABEL = Map.of(
            "TRY_RUN", "试运行",
            "SCHEDULE", "调度",
            "OPENAPI", "开放调用",
            "REPLAY", "重放",
            "MANUAL", "手动"
    );

    private final ApiComponentService apiComponentService;
    private final WorkflowService workflowService;
    private final ScheduleJobService scheduleJobService;
    private final OpenapiAppService openapiAppService;
    private final OpenapiAppWorkflowService openapiAppWorkflowService;
    private final ExecutionInstanceService executionInstanceService;
    private final ExecutionNodeLogService executionNodeLogService;

    @Override
    public LineageVO componentLineage(Long componentId) {
        ApiComponent component = apiComponentService.getById(componentId);
        if (component == null) {
            throw new BizException(ResultCode.NOT_FOUND, "接口组件不存在");
        }
        LineageVO vo = new LineageVO();
        vo.setType("component");
        vo.setSelf(ref(component.getId(), component.getComponentName(), component.getComponentCode(),
                component.getIsPreset() != null && component.getIsPreset() == 1 ? "预置" : "自定义", null));
        List<Workflow> usedBy = new ArrayList<>();
        for (Workflow workflow : workflowService.list()) {
            if (LineageSupport.usesComponent(workflow.getGraphJson(), component.getId(), component.getComponentCode())) {
                usedBy.add(workflow);
            }
        }
        vo.setWorkflows(usedBy.stream().map(this::toWorkflowRef).toList());
        vo.setSchedules(listScheduleRefs(usedBy.stream().map(Workflow::getId).toList()));
        vo.setOpenapiApps(listOpenapiRefs(usedBy.stream().map(Workflow::getId).toList()));
        return vo;
    }

    @Override
    public LineageVO workflowLineage(Long workflowId) {
        Workflow workflow = workflowService.getById(workflowId);
        if (workflow == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        LineageVO vo = new LineageVO();
        vo.setType("workflow");
        vo.setSelf(toWorkflowRef(workflow));
        vo.setComponents(resolveComponents(workflow.getGraphJson()));
        vo.setSchedules(listScheduleRefs(List.of(workflowId)));
        vo.setOpenapiApps(listOpenapiRefs(List.of(workflowId)));
        return vo;
    }

    @Override
    public ExecutionChainVO executionChain(Long executionId) {
        ExecutionInstance instance = executionInstanceService.getById(executionId);
        if (instance == null) {
            throw new BizException(ResultCode.NOT_FOUND, "执行记录不存在");
        }
        List<ExecutionNodeLog> logs = executionNodeLogService.lambdaQuery()
                .eq(ExecutionNodeLog::getExecutionId, executionId)
                .orderByAsc(ExecutionNodeLog::getId)
                .list();

        ExecutionChainVO vo = new ExecutionChainVO();
        vo.setInstance(instance);
        vo.setLogs(logs);
        vo.setFailedNodeId(LineageSupport.firstFailedNodeId(logs.stream()
                .map(item -> new LineageSupport.NodeStatus(item.getNodeId(), item.getStatus()))
                .toList()));

        Workflow workflow = instance.getWorkflowId() == null ? null : workflowService.getById(instance.getWorkflowId());
        if (workflow != null) {
            vo.setWorkflow(toWorkflowRef(workflow));
            vo.setLineage(workflowLineage(workflow.getId()));
        } else {
            LineageVO empty = new LineageVO();
            empty.setType("workflow");
            vo.setLineage(empty);
        }
        vo.setTrigger(resolveTrigger(instance, vo.getLineage()));
        return vo;
    }

    private TriggerSourceVO resolveTrigger(ExecutionInstance instance, LineageVO lineage) {
        TriggerSourceVO trigger = new TriggerSourceVO();
        String type = instance.getTriggerType();
        trigger.setType(type);
        trigger.setLabel(TRIGGER_LABEL.getOrDefault(type, type == null ? "未知" : type));
        trigger.setId(instance.getTriggerAppId());

        if ("OPENAPI".equals(type) && instance.getTriggerAppId() != null) {
            OpenapiApp app = openapiAppService.getById(instance.getTriggerAppId());
            if (app != null) {
                trigger.setName(app.getAppName());
                trigger.setExtra(app.getAppKey());
                return trigger;
            }
        }
        if ("SCHEDULE".equals(type)) {
            if (instance.getTriggerAppId() != null) {
                ScheduleJob job = scheduleJobService.getById(instance.getTriggerAppId());
                if (job != null) {
                    trigger.setName(job.getJobName());
                    trigger.setExtra(job.getScheduleType());
                    return trigger;
                }
            }
            List<LineageRefVO> jobs = lineage == null ? List.of() : lineage.getSchedules();
            if (jobs.size() == 1) {
                trigger.setId(jobs.get(0).getId());
                trigger.setName(jobs.get(0).getName());
                trigger.setExtra(jobs.get(0).getExtra());
                trigger.setInferred(true);
                return trigger;
            }
            if (jobs.size() > 1) {
                trigger.setName(jobs.size() + " 个调度任务");
                trigger.setInferred(true);
                trigger.setExtra("历史调度未记录具体任务，已列出该工作流全部调度");
            }
        }
        return trigger;
    }

    private List<LineageRefVO> resolveComponents(String graphJson) {
        List<LineageSupport.ComponentRef> refs = LineageSupport.extractComponents(graphJson);
        if (refs.isEmpty()) {
            return List.of();
        }
        List<String> codes = refs.stream().map(LineageSupport.ComponentRef::code)
                .filter(StringUtils::hasText).distinct().toList();
        List<Long> ids = refs.stream().map(LineageSupport.ComponentRef::id)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, ApiComponent> byCode = codes.isEmpty()
                ? Map.of()
                : apiComponentService.lambdaQuery().in(ApiComponent::getComponentCode, codes).list().stream()
                .collect(Collectors.toMap(ApiComponent::getComponentCode, item -> item, (a, b) -> a));
        Map<Long, ApiComponent> byId = ids.isEmpty()
                ? Map.of()
                : apiComponentService.listByIds(ids).stream()
                .collect(Collectors.toMap(ApiComponent::getId, item -> item, (a, b) -> a));

        List<LineageRefVO> result = new ArrayList<>();
        for (LineageSupport.ComponentRef ref : refs) {
            ApiComponent component = ref.code() != null ? byCode.get(ref.code()) : null;
            if (component == null && ref.id() != null) {
                component = byId.get(ref.id());
            }
            if (component != null) {
                result.add(ref(component.getId(), component.getComponentName(), component.getComponentCode(),
                        component.getHttpMethod(), component.getUrlTemplate()));
            } else {
                result.add(ref(ref.id(), ref.name() == null ? ref.code() : ref.name(), ref.code(), null, "组件已删除或不存在"));
            }
        }
        return result;
    }

    private List<LineageRefVO> listScheduleRefs(List<Long> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return List.of();
        }
        return scheduleJobService.lambdaQuery()
                .in(ScheduleJob::getWorkflowId, workflowIds)
                .orderByDesc(ScheduleJob::getUpdateTime)
                .list()
                .stream()
                .map(job -> ref(job.getId(), job.getJobName(), null,
                        job.getStatus() != null && job.getStatus() == 1 ? "运行中" : "已停止",
                        job.getScheduleType()))
                .toList();
    }

    private List<LineageRefVO> listOpenapiRefs(List<Long> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return List.of();
        }
        List<OpenapiAppWorkflow> binds = openapiAppWorkflowService.lambdaQuery()
                .in(OpenapiAppWorkflow::getWorkflowId, workflowIds)
                .list();
        if (binds.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> appCount = new LinkedHashMap<>();
        for (OpenapiAppWorkflow bind : binds) {
            if (bind.getAppId() != null) {
                appCount.merge(bind.getAppId(), 1L, Long::sum);
            }
        }
        if (appCount.isEmpty()) {
            return List.of();
        }
        return openapiAppService.listByIds(appCount.keySet()).stream()
                .map(app -> ref(app.getId(), app.getAppName(), app.getAppKey(),
                        app.getStatus() != null && app.getStatus() == 1 ? "启用" : "停用",
                        "授权 " + appCount.getOrDefault(app.getId(), 0L) + " 条"))
                .toList();
    }

    private LineageRefVO toWorkflowRef(Workflow workflow) {
        return ref(workflow.getId(), workflow.getWorkflowName(), workflow.getWorkflowCode(),
                workflow.getStatus(), "v" + (workflow.getVersion() == null ? 1 : workflow.getVersion()));
    }

    private static LineageRefVO ref(Long id, String name, String code, String status, String extra) {
        LineageRefVO vo = new LineageRefVO();
        vo.setId(id);
        vo.setName(name);
        vo.setCode(code);
        vo.setStatus(status);
        vo.setExtra(extra);
        return vo;
    }
}
