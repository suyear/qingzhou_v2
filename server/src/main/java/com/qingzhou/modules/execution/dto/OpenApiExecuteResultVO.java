package com.qingzhou.modules.execution.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 开放平台对外返回：只关心调用结果，不含编排步骤。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenApiExecuteResultVO {
    private Long executionId;
    private String executionNo;
    private String status;
    private Long durationMs;
    private String errorMsg;
    /** 业务输出；单节点时直接为该节点结果，多节点时为 { 节点名: 结果 } */
    private Object output;
}
