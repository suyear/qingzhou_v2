package com.qingzhou.modules.execution.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchReplayResultVO {
    private int success;
    private int skipped;
    private int failed;
    private List<String> messages = new ArrayList<>();
}
