package com.qingzhou.modules.dashboard.dto;

import lombok.Data;

@Data
public class ScheduleHealthVO {

    private long total;
    private long running;
    private long stopped;
    private long recentTriggers;
    private long recentSuccess;
    private long recentFailed;
}
