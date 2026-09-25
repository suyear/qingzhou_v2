package com.qingzhou.modules.component.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ComponentSaveRequest {

    @NotBlank(message = "组件编码不能为空")
    @Size(max = 64, message = "组件编码最长 64")
    private String componentCode;

    @NotBlank(message = "组件名称不能为空")
    @Size(max = 128, message = "组件名称最长 128")
    private String componentName;

    /** WECOM / CUSTOM，创建时默认 CUSTOM */
    private String provider;

    /** MESSAGE / ORG / GROUP / HTTP / DATABASE */
    private String category;

    /** HTTP 方法，或数据库组件的 QUERY / UPDATE */
    private String httpMethod;

    /** HTTP URL 模板，或数据库组件的参数化 SQL */
    private String urlTemplate;

    /** 允许传 JSON 对象或已序列化字符串 */
    private Object headersSchema;
    private Object querySchema;
    private Object bodySchema;
    private Object responseSchema;
    private Object extraConfig;

    private Integer timeoutMs;
    private Integer retryTimes;
    private Integer retryIntervalMs;
    private Integer status;
    private String description;
}
