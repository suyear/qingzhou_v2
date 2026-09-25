package com.qingzhou.modules.execution.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qingzhou.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qz_execution_instance")
public class ExecutionInstance extends BaseEntity {

    private String executionNo;
    private Long workflowId;
    private Integer workflowVersion;
    private Long snapshotId;
    /** MANUAL / TRY_RUN / SCHEDULE / OPENAPI */
    private String triggerType;
    private Long triggerAppId;
    /** PENDING / RUNNING / SUCCESS / FAILED / TIMEOUT / CANCELLED */
    private String status;
    private String inputParams;
    private String outputResult;
    private String errorMsg;
    private String traceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
}
