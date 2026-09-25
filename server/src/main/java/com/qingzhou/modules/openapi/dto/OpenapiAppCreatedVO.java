package com.qingzhou.modules.openapi.dto;

import lombok.Data;

@Data
public class OpenapiAppCreatedVO {
    private Long id;
    private String appName;
    private String appKey;
    /** 明文 Secret 仅创建时回显一次 */
    private String appSecret;
}
