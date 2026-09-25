package com.qingzhou.modules.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserVO {

    private Long id;
    private String username;
    private String displayName;
    private Integer status;
    private Integer mustChangePassword;
    private List<String> roles;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createTime;
}
