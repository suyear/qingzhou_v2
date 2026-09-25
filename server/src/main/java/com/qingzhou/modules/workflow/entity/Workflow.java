package com.qingzhou.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qingzhou.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qz_workflow")
public class Workflow extends AuditEntity {

    private String workflowCode;
    private String workflowName;
    private String description;
    /** DRAFT / PUBLISHED / DISABLED */
    private String status;
    private Integer version;
    /** DAG: {nodes:[], edges:[]} */
    private String graphJson;
    /** 节点间参数映射 JSON */
    private String paramMapping;
    private String inputSchema;
    private String outputSchema;
    /** GLOBAL / INDEPENDENT */
    private String credentialMode;
    private Long credentialId;
    private Integer timeoutMs;
    private String cronExpr;
    private Integer xxlJobId;
    private LocalDateTime publishTime;
}
