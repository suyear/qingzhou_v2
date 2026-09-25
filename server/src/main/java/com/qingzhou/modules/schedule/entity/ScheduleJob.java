package com.qingzhou.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qingzhou.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qz_schedule_job")
public class ScheduleJob extends BaseEntity {

    private Long workflowId;
    private String jobName;
    /** CRON | INTERVAL | DAILY | WEEKLY | ONCE */
    private String scheduleType;
    private String cronExpr;
    private Integer intervalSeconds;
    private LocalDateTime fireAt;
    private String dailyTime;
    private String weekDays;
    /** JSON 字符串，触发时传入工作流 */
    private String triggerInput;
    private Integer xxlJobId;
    private String executorHandler;
    private Integer status;
    private LocalDateTime lastFireTime;
    private LocalDateTime nextFireTime;
    private String remark;

    @TableLogic
    private Integer deleted;
}
