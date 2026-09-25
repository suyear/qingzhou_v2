package com.qingzhou.modules.openapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OpenapiAppBindRequest {

    @NotNull(message = "workflowIds 不能为空")
    private List<Long> workflowIds;
}
