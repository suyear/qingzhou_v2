package com.qingzhou.modules.dashboard.dto;

import lombok.Data;

@Data
public class TopItemVO {

    private Long id;
    private String name;
    private String code;
    private Long total;
    private Long successCount;
    private Long failedCount;
}
