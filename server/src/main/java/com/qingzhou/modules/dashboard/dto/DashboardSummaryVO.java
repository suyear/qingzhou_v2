package com.qingzhou.modules.dashboard.dto;

import lombok.Data;

@Data
public class DashboardSummaryVO {

    private long components;
    private long workflows;
    private long todayExecutions;
    private long runningJobs;
    private long rangeTotal;
    private long rangeSuccess;
    private long rangeFailed;
    /** 0-100，区间内成功数 / 已结束数；无数据时为 null */
    private Double successRate;
}
