package com.qingzhou.modules.dashboard.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DashboardOverviewVO {

    private int days;
    private DashboardSummaryVO summary = new DashboardSummaryVO();
    private List<TrendPointVO> trend = new ArrayList<>();
    private List<NamedCountVO> statusShare = new ArrayList<>();
    private List<NamedCountVO> triggerShare = new ArrayList<>();
    private List<TopItemVO> topWorkflows = new ArrayList<>();
    private List<TopItemVO> topComponents = new ArrayList<>();
    private List<TopItemVO> topFailedWorkflows = new ArrayList<>();
    private List<TopItemVO> topFailedComponents = new ArrayList<>();
    private DurationStatsVO duration = new DurationStatsVO();
    private ScheduleHealthVO scheduleHealth = new ScheduleHealthVO();
}
