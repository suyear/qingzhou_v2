package com.qingzhou.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BootstrapRequest {

    @NotBlank(message = "请输入用户名")
    @Size(min = 3, max = 64)
    private String username;

    @NotBlank(message = "请输入显示名")
    @Size(max = 64)
    private String displayName;

    @NotBlank(message = "请输入密码")
    @Size(min = 8, max = 64, message = "密码至少 8 位")
    private String password;
}
