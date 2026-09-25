package com.qingzhou.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserSaveRequest {

    @NotBlank
    @Size(min = 3, max = 64)
    private String username;

    @NotBlank
    @Size(max = 64)
    private String displayName;

    @Size(min = 8, max = 64)
    private String password;

    @NotEmpty(message = "至少选择一个角色")
    private List<String> roles;

    private Integer status = 1;
}
