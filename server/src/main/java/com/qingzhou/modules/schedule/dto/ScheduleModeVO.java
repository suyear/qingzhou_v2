package com.qingzhou.modules.schedule.dto;

import lombok.Data;

@Data
public class ScheduleModeVO {

    /** true = 未配置 XXL-JOB Admin，进程内 Cron 触发 */
    private boolean localMode;
    private String handler;
    private String hint;
}
