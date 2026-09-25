package com.qingzhou.modules.openapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpenapiAppUpdateRequest {

    @NotBlank(message = "应用名称不能为空")
    private String appName;

    @Min(value = 0, message = "QPS 不能为负数")
    @Max(value = 10000, message = "QPS 最大 10000")
    private Integer rateLimitQps;

    /** JSON 数组或逗号分隔；空表示不限制 */
    private String ipWhitelist;

    private Integer status;
    private String remark;
}
