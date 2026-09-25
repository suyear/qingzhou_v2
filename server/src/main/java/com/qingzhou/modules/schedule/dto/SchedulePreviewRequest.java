package com.qingzhou.modules.schedule.dto;

import lombok.Data;

@Data
public class SchedulePreviewRequest {

    private String scheduleType;
    private String cronExpr;
    private Integer intervalSeconds;
    private String fireAt;
    private String dailyTime;
    private String weekDays;
}
