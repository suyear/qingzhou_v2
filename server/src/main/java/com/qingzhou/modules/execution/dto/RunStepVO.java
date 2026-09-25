package com.qingzhou.modules.execution.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RunStepVO {
    private String nodeId;
    private String nodeName;
    private String componentCode;
    private String status;
    private Long durationMs;
    private String errorMsg;
    /** 解析后的节点响应（已去掉冗余字段） */
    private Object response;
}
