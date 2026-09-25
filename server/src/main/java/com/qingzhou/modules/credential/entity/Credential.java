package com.qingzhou.modules.credential.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.qingzhou.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qz_credential")
public class Credential extends AuditEntity {

    private String credentialName;
    /** WECOM / HTTP_AUTH / DATABASE（兼容 CUSTOM / MYSQL） */
    private String credentialType;
    /** GLOBAL / WORKFLOW */
    private String scope;
    private Long workflowId;
    private String corpId;
    private String agentId;
    /** 【加密存储点】CorpSecret AES-GCM 密文，写入前必须走 AesEncryptor，接口禁止回显 */
    @JsonIgnore
    private String secretCipher;
    private String tokenCacheKey;
    private String extraConfig;
    private Integer status;
    private String remark;
}
