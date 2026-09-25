package com.qingzhou.modules.lineage.dto;

import com.qingzhou.modules.execution.entity.ExecutionInstance;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExecutionChainVO {

    private ExecutionInstance instance;
    private List<ExecutionNodeLog> logs = new ArrayList<>();
    private LineageRefVO workflow;
    private TriggerSourceVO trigger;
    private String failedNodeId;
    private LineageVO lineage;
}
