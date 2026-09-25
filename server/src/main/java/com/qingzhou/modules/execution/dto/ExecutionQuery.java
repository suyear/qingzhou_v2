package com.qingzhou.modules.execution.dto;

import com.qingzhou.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionQuery extends PageQuery {

    private Long workflowId;
    private String triggerType;
    private Long triggerAppId;
    private String status;
    /** 仅看失败/超时，用于问题定位 */
    private Boolean problem;
}
