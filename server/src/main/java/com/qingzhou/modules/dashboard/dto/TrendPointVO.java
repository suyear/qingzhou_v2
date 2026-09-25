package com.qingzhou.modules.dashboard.dto;

import lombok.Data;

@Data
public class TrendPointVO {

    /** yyyy-MM-dd */
    private String date;
    private long total;
    private long successCount;
    private long failedCount;
    private long runningCount;
    /** 当日已结束执行的失败率 0-100；无已结束数据时为 null */
    private Double failRate;
}
