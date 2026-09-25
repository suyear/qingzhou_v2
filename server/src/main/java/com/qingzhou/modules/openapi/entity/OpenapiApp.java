package com.qingzhou.modules.openapi.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.qingzhou.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qz_openapi_app")
public class OpenapiApp extends AuditEntity {

    private String appName;
    private String appKey;
    /** 【加密存储点】HMAC Secret，接口层禁止回显 */
    @JsonIgnore
    private String appSecretCipher;
    private Integer status;
    private Integer rateLimitQps;
    /** 允许更新为 null，表示清空白名单、不限制 IP */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ipWhitelist;
    private LocalDateTime expireTime;
    private String remark;
}
