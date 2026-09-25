package com.qingzhou.modules.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthSessionVO {

    private String token;
    private Long userId;
    private String username;
    private String displayName;
    private List<String> roles;
    private boolean mustChangePassword;
    private long expiresInSeconds;
}
