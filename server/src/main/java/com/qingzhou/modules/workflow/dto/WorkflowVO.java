package com.qingzhou.modules.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkflowVO {

    private Long id;
    private String workflowCode;
    private String workflowName;
    private String description;
    private String status;
    private Integer version;
    private DagGraph graph;
    private List<ParamMappingItem> paramMapping;
    private Object inputSchema;
    private Object outputSchema;
    private String credentialMode;
    private Long credentialId;
    private Integer timeoutMs;
    private String cronExpr;
    private Integer xxlJobId;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
