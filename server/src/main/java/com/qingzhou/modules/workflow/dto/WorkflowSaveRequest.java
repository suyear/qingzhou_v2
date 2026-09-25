package com.qingzhou.modules.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkflowSaveRequest {

    @NotBlank(message = "工作流编码不能为空")
    @Size(max = 64, message = "工作流编码最长 64")
    private String workflowCode;

    @NotBlank(message = "工作流名称不能为空")
    @Size(max = 128, message = "工作流名称最长 128")
    private String workflowName;

    private String description;

    /** DAG：nodes + edges */
    @Valid
    private DagGraph graph;

    /** 节点间参数映射，序列化后写入 param_mapping */
    @Valid
    private List<ParamMappingItem> paramMapping = new ArrayList<>();

    private Object inputSchema;
    private Object outputSchema;
    /** GLOBAL / INDEPENDENT */
    private String credentialMode;
    private Long credentialId;
    private Integer timeoutMs;
    private String cronExpr;
}
