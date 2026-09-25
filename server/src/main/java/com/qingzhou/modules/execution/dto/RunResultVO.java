package com.qingzhou.modules.execution.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 试跑 / 开放网关 / 立即触发共用的精简运行结果。
 * 完整 instance + logs 仅用于运行结果详情。
 */
@Data
@Builder
public class RunResultVO {
    private Long executionId;
    private String executionNo;
    private String status;
    private Long durationMs;
    private String errorMsg;
    /** 业务输出（按节点），已清洗冗余字段 */
    private Map<String, Object> output;
    @Builder.Default
    private List<RunStepVO> steps = new ArrayList<>();
}
