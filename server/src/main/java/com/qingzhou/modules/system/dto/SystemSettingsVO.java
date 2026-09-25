package com.qingzhou.modules.system.dto;

import lombok.Data;

@Data
public class SystemSettingsVO {

    private String siteName;
    private String openapiPublicBaseUrl;
    private Integer executionRetentionDays;
    private String timezone;
}
