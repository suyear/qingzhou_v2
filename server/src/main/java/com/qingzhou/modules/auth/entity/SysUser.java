package com.qingzhou.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qingzhou.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qz_user")
public class SysUser extends AuditEntity {

    private String username;

    private String displayName;

    private String passwordHash;

    private Integer status;

    private Integer mustChangePassword;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;
}
