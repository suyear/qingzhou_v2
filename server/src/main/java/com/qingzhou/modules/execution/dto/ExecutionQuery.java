package com.qingzhou.modules.execution.dto;

import com.qingzhou.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionQuery extends PageQuery {

    private Long workflowId;
    private String triggerType;
    private Long triggerAppId;
    private String status;
    /** 仅看失败/超时，用于问题定位 */
    private Boolean problem;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTimeFrom;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTimeTo;

    /** 耗时下限（毫秒），用于找慢单 */
    private Long minDurationMs;
}
