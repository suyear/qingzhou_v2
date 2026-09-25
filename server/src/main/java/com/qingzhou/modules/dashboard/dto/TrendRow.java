package com.qingzhou.modules.dashboard.dto;

import lombok.Data;

@Data
public class TrendRow {

    private String bucket;
    private Long total;
    private Long successCount;
    private Long failedCount;
    private Long runningCount;
}
