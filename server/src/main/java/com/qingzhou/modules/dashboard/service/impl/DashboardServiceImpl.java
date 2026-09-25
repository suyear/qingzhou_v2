package com.qingzhou.modules.dashboard.service.impl;

import com.qingzhou.modules.component.entity.ApiComponent;
import com.qingzhou.modules.component.service.ApiComponentService;
import com.qingzhou.modules.dashboard.dto.DashboardOverviewVO;
import com.qingzhou.modules.dashboard.dto.DashboardSummaryVO;
import com.qingzhou.modules.dashboard.dto.DurationStatsVO;
import com.qingzhou.modules.dashboard.dto.NamedCountVO;
import com.qingzhou.modules.dashboard.dto.ScheduleHealthVO;
import com.qingzhou.modules.dashboard.dto.TopItemVO;
import com.qingzhou.modules.dashboard.mapper.DashboardMapper;
import com.qingzhou.modules.dashboard.service.DashboardService;
import com.qingzhou.modules.dashboard.support.DashboardStatsSupport;
import com.qingzhou.modules.schedule.entity.ScheduleJob;
import com.qingzhou.modules.schedule.service.ScheduleJobService;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final Map<String, String> STATUS_LABEL = Map.of(
            "SUCCESS", "成功",
            "FAILED", "失败",
            "RUNNING", "运行中",
            "TIMEOUT", "超时",
            "PENDING", "排队中",
            "CANCELLED", "已取消"
    );

    private static final Map<String, String> TRIGGER_LABEL = Map.of(
            "TRY_RUN", "试运行",
            "SCHEDULE", "调度",
            "OPENAPI", "开放调用",
            "REPLAY", "重放",
            "MANUAL", "手动"
    );

    private final DashboardMapper dashboardMapper;
    private final ApiComponentService apiComponentService;
    private final WorkflowService workflowService;
    private final ScheduleJobService scheduleJobService;

    @Override
    public DashboardOverviewVO overview(Integer daysParam) {
        int days = DashboardStatsSupport.clampDays(daysParam);
        LocalDate today = LocalDate.now();
        LocalDateTime since = DashboardStatsSupport.rangeStart(days, today);
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();

        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setDays(days);

        DashboardSummaryVO summary = vo.getSummary();
        summary.setComponents(apiComponentService.count());
        summary.setWorkflows(workflowService.count());
        summary.setRunningJobs(scheduleJobService.lambdaQuery().eq(ScheduleJob::getStatus, 1).count());
        summary.setTodayExecutions(dashboardMapper.countBetween(startOfToday, startOfTomorrow));

        List<NamedCountVO> statusShare = emptyIfNull(dashboardMapper.selectStatusShare(since));
        for (NamedCountVO item : statusShare) {
            String name = item.getName();
            item.setLabel(STATUS_LABEL.getOrDefault(name, name));
            item.setCount(DashboardStatsSupport.nz(item.getCount()));
        }
        vo.setStatusShare(statusShare);

        long rangeSuccess = 0;
        long rangeFailed = 0;
        long rangeTotal = 0;
        for (NamedCountVO item : statusShare) {
            rangeTotal += item.getCount();
            if ("SUCCESS".equals(item.getName())) {
                rangeSuccess = item.getCount();
            } else if ("FAILED".equals(item.getName()) || "TIMEOUT".equals(item.getName())) {
                rangeFailed += item.getCount();
            }
        }
        summary.setRangeTotal(rangeTotal);
        summary.setRangeSuccess(rangeSuccess);
        summary.setRangeFailed(rangeFailed);
        summary.setSuccessRate(DashboardStatsSupport.successRate(rangeSuccess, rangeFailed));

        List<NamedCountVO> triggerShare = emptyIfNull(dashboardMapper.selectTriggerShare(since));
        for (NamedCountVO item : triggerShare) {
            String name = item.getName();
            item.setLabel(TRIGGER_LABEL.getOrDefault(name, name == null ? "未知" : name));
            item.setCount(DashboardStatsSupport.nz(item.getCount()));
        }
        vo.setTriggerShare(triggerShare);

        vo.setTrend(DashboardStatsSupport.fillTrend(dashboardMapper.selectTrend(since), days, today));
        vo.setTopWorkflows(fillWorkflowNames(emptyIfNull(dashboardMapper.selectTopWorkflows(since, 5))));
        vo.setTopComponents(fillComponentNames(emptyIfNull(dashboardMapper.selectTopComponents(since, 5))));
        vo.setTopFailedWorkflows(fillWorkflowNames(emptyIfNull(dashboardMapper.selectTopFailedWorkflows(since, 5))));
        vo.setTopFailedComponents(fillComponentNames(emptyIfNull(dashboardMapper.selectTopFailedComponents(since, 5))));
        vo.setDuration(buildDuration(emptyIfNull(dashboardMapper.selectDurations(since))));
        vo.setScheduleHealth(buildScheduleHealth(since));
        return vo;
    }

    private DurationStatsVO buildDuration(List<Long> durations) {
        DurationStatsVO duration = new DurationStatsVO();
        duration.setSampleCount(durations.size());
        duration.setAvgMs(DashboardStatsSupport.averageMs(durations));
        duration.setP95Ms(DashboardStatsSupport.percentileMs(durations, 0.95));
        return duration;
    }

    private ScheduleHealthVO buildScheduleHealth(LocalDateTime since) {
        ScheduleHealthVO health = new ScheduleHealthVO();
        long total = scheduleJobService.count();
        long running = scheduleJobService.lambdaQuery().eq(ScheduleJob::getStatus, 1).count();
        health.setTotal(total);
        health.setRunning(running);
        health.setStopped(Math.max(0, total - running));

        long recentSuccess = 0;
        long recentFailed = 0;
        long recentTriggers = 0;
        for (NamedCountVO item : emptyIfNull(dashboardMapper.selectStatusShareByTrigger(since, "SCHEDULE"))) {
            long count = DashboardStatsSupport.nz(item.getCount());
            recentTriggers += count;
            if ("SUCCESS".equals(item.getName())) {
                recentSuccess = count;
            } else if ("FAILED".equals(item.getName()) || "TIMEOUT".equals(item.getName())) {
                recentFailed += count;
            }
        }
        health.setRecentTriggers(recentTriggers);
        health.setRecentSuccess(recentSuccess);
        health.setRecentFailed(recentFailed);
        return health;
    }

    private List<TopItemVO> fillWorkflowNames(List<TopItemVO> items) {
        List<Long> ids = items.stream().map(TopItemVO::getId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Workflow> workflows = ids.isEmpty()
                ? Map.of()
                : workflowService.listByIds(ids).stream()
                .collect(Collectors.toMap(Workflow::getId, item -> item, (a, b) -> a));
        for (TopItemVO item : items) {
            item.setTotal(DashboardStatsSupport.nz(item.getTotal()));
            item.setSuccessCount(DashboardStatsSupport.nz(item.getSuccessCount()));
            item.setFailedCount(nzFailed(item));
            Workflow workflow = workflows.get(item.getId());
            if (workflow != null) {
                item.setName(workflow.getWorkflowName());
                item.setCode(workflow.getWorkflowCode());
            } else {
                item.setName("工作流 #" + item.getId());
            }
        }
        return items;
    }

    private List<TopItemVO> fillComponentNames(List<TopItemVO> items) {
        List<String> codes = items.stream()
                .map(TopItemVO::getCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, ApiComponent> components = codes.isEmpty()
                ? Map.of()
                : apiComponentService.lambdaQuery()
                .in(ApiComponent::getComponentCode, codes)
                .list()
                .stream()
                .collect(Collectors.toMap(ApiComponent::getComponentCode, item -> item, (a, b) -> a));
        for (TopItemVO item : items) {
            item.setTotal(DashboardStatsSupport.nz(item.getTotal()));
            item.setSuccessCount(DashboardStatsSupport.nz(item.getSuccessCount()));
            item.setFailedCount(nzFailed(item));
            ApiComponent component = components.get(item.getCode());
            if (component != null) {
                item.setId(component.getId());
                item.setName(component.getComponentName());
            } else {
                item.setName(item.getCode());
            }
        }
        return items;
    }

    private static long nzFailed(TopItemVO item) {
        if (item.getFailedCount() != null) {
            return DashboardStatsSupport.nz(item.getFailedCount());
        }
        return Math.max(0, DashboardStatsSupport.nz(item.getTotal()) - DashboardStatsSupport.nz(item.getSuccessCount()));
    }

    private static <T> List<T> emptyIfNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
