package com.qingzhou.modules.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParamMappingItem {

    @NotBlank(message = "fromNode 不能为空")
    private String fromNode;

    @NotBlank(message = "fromPath 不能为空")
    private String fromPath;

    @NotBlank(message = "toNode 不能为空")
    private String toNode;

    @NotBlank(message = "toPath 不能为空")
    private String toPath;
}
