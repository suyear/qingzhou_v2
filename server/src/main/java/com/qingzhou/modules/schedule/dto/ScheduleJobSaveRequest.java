package com.qingzhou.modules.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ScheduleJobSaveRequest {

    @NotNull(message = "工作流 ID 不能为空")
    private Long workflowId;

    private String jobName;

    /** CRON | INTERVAL | DAILY | WEEKLY | ONCE，默认 CRON */
    private String scheduleType;

    private String cronExpr;

    private Integer intervalSeconds;

    /** ISO 本地时间，如 2026-09-10T08:30:00 */
    private String fireAt;

    /** HH:mm */
    private String dailyTime;

    /** 1-7 逗号分隔 */
    private String weekDays;

    private Map<String, Object> triggerInput;

    private String remark;

    /** 创建后是否立即启动 */
    private Boolean start;
}
