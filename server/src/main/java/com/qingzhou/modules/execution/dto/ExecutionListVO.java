package com.qingzhou.modules.execution.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExecutionListVO {

    private Long id;
    private String executionNo;
    private Long workflowId;
    private String workflowName;
    private String workflowCode;
    private Integer workflowVersion;
    private Long snapshotId;
    private String triggerType;
    private Long triggerAppId;
    /** 触发来源可读名：开放应用名 / 调度任务名等 */
    private String triggerSourceLabel;
    private String status;
    private String errorMsg;
    /** TIMEOUT / AUTH / PARAM / UPSTREAM / BUSINESS / UNKNOWN */
    private String failureCategory;
    private String failureCategoryLabel;
    private String traceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
}
