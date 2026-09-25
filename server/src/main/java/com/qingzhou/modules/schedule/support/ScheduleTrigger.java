package com.qingzhou.modules.schedule.support;

import com.qingzhou.common.json.Jsons;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.engine.WorkflowEngine;
import com.qingzhou.modules.schedule.entity.ScheduleJob;
import com.qingzhou.modules.schedule.mapper.ScheduleJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * XXL-JOB Handler 与内置调度共用的触发入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleTrigger {

    private final WorkflowEngine workflowEngine;
    private final ScheduleJobMapper scheduleJobMapper;
    private final Jsons jsons;

    public ExecutionVO fireWorkflow(Long workflowId) {
        log.info("调度触发工作流 workflowId={}", workflowId);
        return workflowEngine.run(workflowId, "SCHEDULE", null, Map.of());
    }

    public ExecutionVO fireJob(ScheduleJob job) {
        log.info("触发调度 jobId={} workflowId={}", job.getId(), job.getWorkflowId());
        ExecutionVO vo = workflowEngine.run(job.getWorkflowId(), "SCHEDULE", job.getId(), parseTriggerInput(job));
        touch(job, LocalDateTime.now());
        return vo;
    }

    private void touch(ScheduleJob job, LocalDateTime now) {
        job.setLastFireTime(now);
        try {
            job.setNextFireTime(ScheduleSupport.nextFireTime(job));
        } catch (Exception ignored) {
            // 规则非法时仍记录上次触发时间
        }
        if (ScheduleType.of(job.getScheduleType()) == ScheduleType.ONCE) {
            job.setNextFireTime(null);
        }
        scheduleJobMapper.updateById(job);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTriggerInput(ScheduleJob job) {
        if (!StringUtils.hasText(job.getTriggerInput())) {
            return Map.of();
        }
        Object parsed = jsons.fromJson(job.getTriggerInput(), Object.class);
        if (parsed instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }
}
