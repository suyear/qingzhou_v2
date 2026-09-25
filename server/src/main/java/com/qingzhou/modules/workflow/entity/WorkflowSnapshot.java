package com.qingzhou.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("qz_workflow_snapshot")
public class WorkflowSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workflowId;
    private Integer version;
    private String graphJson;
    private String paramMapping;
    private String inputSchema;
    private String outputSchema;
    private String credentialMode;
    private Long credentialId;
    private Integer timeoutMs;
    private LocalDateTime publishTime;
    private String publishBy;
}
