package com.qingzhou.modules.execution.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class TryRunRequest {
    private Map<String, Object> input = new HashMap<>();
}
