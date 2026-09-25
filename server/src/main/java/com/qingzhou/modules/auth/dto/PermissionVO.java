package com.qingzhou.modules.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PermissionVO {
    private Long id;
    private String permCode;
    private String permName;
    private String permType;
    private String parentCode;
    private Integer sortOrder;
}
