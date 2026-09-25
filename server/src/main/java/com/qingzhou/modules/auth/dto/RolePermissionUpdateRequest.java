package com.qingzhou.modules.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RolePermissionUpdateRequest {

    @NotNull
    private List<String> permissions;
}
