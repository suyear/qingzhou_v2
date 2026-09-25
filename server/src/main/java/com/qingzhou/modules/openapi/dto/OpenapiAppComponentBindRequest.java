package com.qingzhou.modules.openapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OpenapiAppComponentBindRequest {

    @NotNull(message = "componentIds 不能为空")
    private List<Long> componentIds;
}
