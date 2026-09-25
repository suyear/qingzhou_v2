package com.qingzhou.modules.schedule.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SchedulePreviewVO {

    private String summary;
    private String effectiveCron;
    private List<LocalDateTime> nextTimes;
}
