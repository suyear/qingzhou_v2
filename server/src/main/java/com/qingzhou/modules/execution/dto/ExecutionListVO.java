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
    private String status;
    private String errorMsg;
    private String traceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
}
