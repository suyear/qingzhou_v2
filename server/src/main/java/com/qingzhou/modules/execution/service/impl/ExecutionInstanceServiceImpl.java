package com.qingzhou.modules.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.execution.dto.ExecutionListVO;
import com.qingzhou.modules.execution.dto.ExecutionQuery;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.entity.ExecutionInstance;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import com.qingzhou.modules.execution.mapper.ExecutionInstanceMapper;
import com.qingzhou.modules.execution.service.ExecutionInstanceService;
import com.qingzhou.modules.execution.service.ExecutionNodeLogService;
import com.qingzhou.modules.execution.support.FailureCategory;
import com.qingzhou.modules.openapi.entity.OpenapiApp;
import com.qingzhou.modules.openapi.mapper.OpenapiAppMapper;
import com.qingzhou.modules.schedule.entity.ScheduleJob;
import com.qingzhou.modules.schedule.mapper.ScheduleJobMapper;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExecutionInstanceServiceImpl extends ServiceImpl<ExecutionInstanceMapper, ExecutionInstance>
        implements ExecutionInstanceService {

    private final ExecutionNodeLogService executionNodeLogService;
    private final WorkflowService workflowService;
    private final OpenapiAppMapper openapiAppMapper;
    private final ScheduleJobMapper scheduleJobMapper;

    @Override
    public IPage<ExecutionListVO> pageExecutions(ExecutionQuery query) {
        LambdaQueryWrapper<ExecutionInstance> wrapper = buildWrapper(query);
        wrapper.orderByDesc(ExecutionInstance::getCreateTime);
        IPage<ExecutionInstance> page = page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        Page<ExecutionListVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(toListVos(page.getRecords()));
        return result;
    }

    @Override
    public List<ExecutionListVO> listForExport(ExecutionQuery query, int maxRows) {
        int limit = Math.max(1, Math.min(maxRows, 5000));
        LambdaQueryWrapper<ExecutionInstance> wrapper = buildWrapper(query);
        wrapper.orderByDesc(ExecutionInstance::getCreateTime).last("LIMIT " + limit);
        return toListVos(list(wrapper));
    }

    @Override
    public ExecutionVO detail(Long id) {
        ExecutionInstance instance = getById(id);
        if (instance == null) {
            throw new BizException(ResultCode.NOT_FOUND, "执行记录不存在");
        }
        ExecutionVO vo = new ExecutionVO();
        vo.setInstance(instance);
        vo.setLogs(executionNodeLogService.lambdaQuery()
                .eq(ExecutionNodeLog::getExecutionId, id)
                .orderByAsc(ExecutionNodeLog::getId)
                .list());
        return vo;
    }

    @Override
    @Transactional
    public int purgeExpired(int retentionDays) {
        int days = Math.max(7, retentionDays);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        List<ExecutionInstance> expired = lambdaQuery()
                .lt(ExecutionInstance::getCreateTime, cutoff)
                .select(ExecutionInstance::getId)
                .last("LIMIT 2000")
                .list();
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = expired.stream().map(ExecutionInstance::getId).toList();
        executionNodeLogService.remove(new LambdaQueryWrapper<ExecutionNodeLog>()
                .in(ExecutionNodeLog::getExecutionId, ids));
        removeByIds(ids);
        return ids.size();
    }

    private LambdaQueryWrapper<ExecutionInstance> buildWrapper(ExecutionQuery query) {
        LambdaQueryWrapper<ExecutionInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getWorkflowId() != null, ExecutionInstance::getWorkflowId, query.getWorkflowId())
                .eq(StringUtils.hasText(query.getTriggerType()), ExecutionInstance::getTriggerType, query.getTriggerType())
                .eq(query.getTriggerAppId() != null, ExecutionInstance::getTriggerAppId, query.getTriggerAppId())
                .ge(query.getStartTimeFrom() != null, ExecutionInstance::getStartTime, query.getStartTimeFrom())
                .le(query.getStartTimeTo() != null, ExecutionInstance::getStartTime, query.getStartTimeTo())
                .ge(query.getMinDurationMs() != null, ExecutionInstance::getDurationMs, query.getMinDurationMs());
        if (Boolean.TRUE.equals(query.getProblem())) {
            wrapper.in(ExecutionInstance::getStatus, List.of("FAILED", "TIMEOUT"));
        } else {
            wrapper.eq(StringUtils.hasText(query.getStatus()), ExecutionInstance::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w
                    .like(ExecutionInstance::getExecutionNo, kw)
                    .or()
                    .like(ExecutionInstance::getTraceId, kw)
                    .or()
                    .like(ExecutionInstance::getErrorMsg, kw));
        }
        return wrapper;
    }

    private List<ExecutionListVO> toListVos(List<ExecutionInstance> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> workflowIds = records.stream()
                .map(ExecutionInstance::getWorkflowId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Workflow> workflows = workflowIds.isEmpty()
                ? Map.of()
                : workflowService.listByIds(workflowIds).stream()
                .collect(Collectors.toMap(Workflow::getId, item -> item, (a, b) -> a));

        Set<Long> openapiIds = new HashSet<>();
        Set<Long> scheduleIds = new HashSet<>();
        for (ExecutionInstance item : records) {
            if (item.getTriggerAppId() == null) {
                continue;
            }
            if ("OPENAPI".equals(item.getTriggerType())) {
                openapiIds.add(item.getTriggerAppId());
            } else if ("SCHEDULE".equals(item.getTriggerType())) {
                scheduleIds.add(item.getTriggerAppId());
            } else if ("REPLAY".equals(item.getTriggerType())) {
                openapiIds.add(item.getTriggerAppId());
                scheduleIds.add(item.getTriggerAppId());
            }
        }

        Map<Long, String> appNames = openapiIds.isEmpty()
                ? Map.of()
                : openapiAppMapper.selectList(new LambdaQueryWrapper<OpenapiApp>().in(OpenapiApp::getId, openapiIds))
                .stream()
                .collect(Collectors.toMap(OpenapiApp::getId, OpenapiApp::getAppName, (a, b) -> a));
        Map<Long, String> jobNames = scheduleIds.isEmpty()
                ? Map.of()
                : scheduleJobMapper.selectList(new LambdaQueryWrapper<ScheduleJob>().in(ScheduleJob::getId, scheduleIds))
                .stream()
                .collect(Collectors.toMap(ScheduleJob::getId, ScheduleJob::getJobName, (a, b) -> a));

        List<ExecutionListVO> result = new ArrayList<>(records.size());
        for (ExecutionInstance item : records) {
            result.add(toListVo(item, workflows.get(item.getWorkflowId()), appNames, jobNames));
        }
        return result;
    }

    private ExecutionListVO toListVo(
            ExecutionInstance item,
            Workflow workflow,
            Map<Long, String> appNames,
            Map<Long, String> jobNames) {
        ExecutionListVO vo = new ExecutionListVO();
        vo.setId(item.getId());
        vo.setExecutionNo(item.getExecutionNo());
        vo.setWorkflowId(item.getWorkflowId());
        vo.setWorkflowVersion(item.getWorkflowVersion());
        vo.setSnapshotId(item.getSnapshotId());
        vo.setTriggerType(item.getTriggerType());
        vo.setTriggerAppId(item.getTriggerAppId());
        vo.setTriggerSourceLabel(resolveSourceLabel(item, appNames, jobNames));
        vo.setStatus(item.getStatus());
        vo.setErrorMsg(item.getErrorMsg());
        String category = FailureCategory.classify(item.getStatus(), item.getErrorMsg());
        vo.setFailureCategory(category);
        vo.setFailureCategoryLabel(FailureCategory.label(category));
        vo.setTraceId(item.getTraceId());
        vo.setStartTime(item.getStartTime());
        vo.setEndTime(item.getEndTime());
        vo.setDurationMs(item.getDurationMs());
        if (workflow != null) {
            vo.setWorkflowName(workflow.getWorkflowName());
            vo.setWorkflowCode(workflow.getWorkflowCode());
        }
        return vo;
    }

    private static String resolveSourceLabel(
            ExecutionInstance item,
            Map<Long, String> appNames,
            Map<Long, String> jobNames) {
        Long id = item.getTriggerAppId();
        String type = item.getTriggerType();
        if ("OPENAPI".equals(type) && id != null) {
            return appNames.getOrDefault(id, "应用 #" + id);
        }
        if ("SCHEDULE".equals(type) && id != null) {
            return jobNames.getOrDefault(id, "调度 #" + id);
        }
        if ("REPLAY".equals(type) && id != null) {
            if (appNames.containsKey(id)) {
                return "重放 · " + appNames.get(id);
            }
            if (jobNames.containsKey(id)) {
                return "重放 · " + jobNames.get(id);
            }
        }
        if ("TRY_RUN".equals(type) || "MANUAL".equals(type)) {
            return "设计器";
        }
        if ("REPLAY".equals(type)) {
            return "重放";
        }
        return null;
    }
}
