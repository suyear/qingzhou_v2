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
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExecutionInstanceServiceImpl extends ServiceImpl<ExecutionInstanceMapper, ExecutionInstance>
        implements ExecutionInstanceService {

    private final ExecutionNodeLogService executionNodeLogService;
    private final WorkflowService workflowService;

    @Override
    public IPage<ExecutionListVO> pageExecutions(ExecutionQuery query) {
        LambdaQueryWrapper<ExecutionInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getWorkflowId() != null, ExecutionInstance::getWorkflowId, query.getWorkflowId())
                .eq(StringUtils.hasText(query.getTriggerType()), ExecutionInstance::getTriggerType, query.getTriggerType())
                .eq(query.getTriggerAppId() != null, ExecutionInstance::getTriggerAppId, query.getTriggerAppId());
        if (Boolean.TRUE.equals(query.getProblem())) {
            wrapper.in(ExecutionInstance::getStatus, List.of("FAILED", "TIMEOUT"));
        } else {
            wrapper.eq(StringUtils.hasText(query.getStatus()), ExecutionInstance::getStatus, query.getStatus());
        }
        wrapper
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(ExecutionInstance::getExecutionNo, query.getKeyword())
                        .or()
                        .like(ExecutionInstance::getTraceId, query.getKeyword()))
                .orderByDesc(ExecutionInstance::getCreateTime);
        IPage<ExecutionInstance> page = page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        List<Long> workflowIds = page.getRecords().stream()
                .map(ExecutionInstance::getWorkflowId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Workflow> workflows = workflowIds.isEmpty()
                ? Map.of()
                : workflowService.listByIds(workflowIds).stream()
                .collect(Collectors.toMap(Workflow::getId, item -> item, (a, b) -> a));
        return page.convert(item -> toListVo(item, workflows.get(item.getWorkflowId())));
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

    private ExecutionListVO toListVo(ExecutionInstance item, Workflow workflow) {
        ExecutionListVO vo = new ExecutionListVO();
        vo.setId(item.getId());
        vo.setExecutionNo(item.getExecutionNo());
        vo.setWorkflowId(item.getWorkflowId());
        vo.setWorkflowVersion(item.getWorkflowVersion());
        vo.setSnapshotId(item.getSnapshotId());
        vo.setTriggerType(item.getTriggerType());
        vo.setTriggerAppId(item.getTriggerAppId());
        vo.setStatus(item.getStatus());
        vo.setErrorMsg(item.getErrorMsg());
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
}
