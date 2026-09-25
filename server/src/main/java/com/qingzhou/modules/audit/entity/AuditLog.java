package com.qingzhou.modules.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qz_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long actorId;

    private String actorName;

    private String action;

    private String resourceType;

    private String resourceId;

    private String result;

    private String summary;

    private String detailJson;

    private String clientIp;

    private LocalDateTime createTime;
}
