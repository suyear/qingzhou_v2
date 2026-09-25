package com.qingzhou.modules.license.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("qz_license")
public class ProductLicense {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String licensePayload;

    private String signature;

    private String issuer;

    private String customer;

    private LocalDate expiresAt;

    private Integer seats;

    private String featuresJson;

    private String status;

    private LocalDateTime importedAt;

    private String importedBy;
}
