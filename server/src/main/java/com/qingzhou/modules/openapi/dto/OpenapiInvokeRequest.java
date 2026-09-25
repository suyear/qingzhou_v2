package com.qingzhou.modules.openapi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class OpenapiInvokeRequest {

    @NotBlank(message = "工作流编码不能为空")
    private String workflowCode;

    private Map<String, Object> input = new LinkedHashMap<>();
}
