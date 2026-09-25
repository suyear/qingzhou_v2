package com.qingzhou.modules.dashboard.dto;

import lombok.Data;

@Data
public class DurationStatsVO {

    private long sampleCount;
    private Long avgMs;
    private Long p95Ms;
}
