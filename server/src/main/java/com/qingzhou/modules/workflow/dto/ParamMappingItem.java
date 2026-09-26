package com.qingzhou.modules.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParamMappingItem {

    @NotBlank(message = "fromNode 不能为空")
    private String fromNode;

    /**
     * JSON Path；空或 "$" 表示取 fromNode 的完整对象（整步请求/响应）。
     */
    private String fromPath;

    @NotBlank(message = "toNode 不能为空")
    private String toNode;

    @NotBlank(message = "toPath 不能为空")
    private String toPath;

    /**
     * 取值来源：output（默认，前序响应）/ request（前序请求 payload）/ input（与 fromNode=__input__ 等价）。
     */
    private String fromSource;
}
