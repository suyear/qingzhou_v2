package com.qingzhou.modules.openapi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class OpenapiInvokeRequest {

    /** WORKFLOW | COMPONENT，默认 WORKFLOW */
    private String resourceType = "WORKFLOW";

    /** 资源编码：工作流 workflowCode 或组件 componentCode */
    private String resourceCode;

    /** 兼容旧字段 */
    private String workflowCode;

    private Map<String, Object> input = new LinkedHashMap<>();

    public String resolvedType() {
        String type = StringUtils.hasText(resourceType) ? resourceType.trim().toUpperCase() : "WORKFLOW";
        return "COMPONENT".equals(type) ? "COMPONENT" : "WORKFLOW";
    }

    public String resolvedCode() {
        if (StringUtils.hasText(resourceCode)) {
            return resourceCode.trim();
        }
        if (StringUtils.hasText(workflowCode)) {
            return workflowCode.trim();
        }
        return "";
    }

    public void validateCode() {
        if (!StringUtils.hasText(resolvedCode())) {
            throw new IllegalArgumentException("资源编码不能为空");
        }
    }
}
