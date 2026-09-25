package com.qingzhou.modules.execution.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchReplayRequest {
    private List<Long> ids = new ArrayList<>();
}
