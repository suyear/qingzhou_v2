package com.qingzhou.modules.component.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qingzhou.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qz_api_component")
public class ApiComponent extends AuditEntity {

    private String componentCode;
    private String componentName;
    /** WECOM / CUSTOM */
    private String provider;
    /** MESSAGE / ORG / GROUP / HTTP */
    private String category;
    private String httpMethod;
    private String urlTemplate;
    /** JSON Schema 字符串，画布入参预览来源 */
    private String headersSchema;
    private String querySchema;
    private String bodySchema;
    private String responseSchema;
    private Integer timeoutMs;
    private Integer retryTimes;
    private Integer retryIntervalMs;
    private Integer isPreset;
    private Integer status;
    private String description;
    private String extraConfig;
}
