package com.qingzhou.modules.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RoleVO {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private List<String> permissions;
    private long userCount;
}
