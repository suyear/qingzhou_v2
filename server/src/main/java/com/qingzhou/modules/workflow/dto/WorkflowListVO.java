package com.qingzhou.modules.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 工作流列表轻量视图：不含 graphJson / schema 等大字段 */
@Data
public class WorkflowListVO {

    private Long id;
    private String workflowCode;
    private String workflowName;
    private String description;
    private String status;
    private Integer version;
    private String credentialMode;
    private Long credentialId;
    private Integer timeoutMs;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static WorkflowListVO from(com.qingzhou.modules.workflow.entity.Workflow entity) {
        if (entity == null) {
            return null;
        }
        WorkflowListVO vo = new WorkflowListVO();
        vo.setId(entity.getId());
        vo.setWorkflowCode(entity.getWorkflowCode());
        vo.setWorkflowName(entity.getWorkflowName());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        vo.setVersion(entity.getVersion());
        vo.setCredentialMode(entity.getCredentialMode());
        vo.setCredentialId(entity.getCredentialId());
        vo.setTimeoutMs(entity.getTimeoutMs());
        vo.setPublishTime(entity.getPublishTime());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
