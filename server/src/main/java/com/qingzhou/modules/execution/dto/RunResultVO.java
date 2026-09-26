package com.qingzhou.modules.execution.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
    /** 对外投影结果，与开放 API data.output / publicOutput 同形态 */
    private Object output;
    @Builder.Default
    private List<RunStepVO> steps = new ArrayList<>();
}
