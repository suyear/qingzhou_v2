package com.qingzhou.modules.openapi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpenapiAppCreateRequest {
    @NotBlank(message = "应用名称不能为空")
    private String appName;
    private Integer rateLimitQps;
    private String ipWhitelist;
    private String remark;
}
