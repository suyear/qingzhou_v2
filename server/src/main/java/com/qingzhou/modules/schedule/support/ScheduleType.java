package com.qingzhou.modules.schedule.support;

public enum ScheduleType {

    CRON,
    INTERVAL,
    DAILY,
    WEEKLY,
    ONCE;

    public static ScheduleType of(String value) {
        if (value == null || value.isBlank()) {
            return CRON;
        }
        try {
            return ScheduleType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return CRON;
        }
    }
}
