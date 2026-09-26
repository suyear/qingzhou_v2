package com.qingzhou.modules.execution.dto;

import com.qingzhou.modules.execution.entity.ExecutionInstance;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExecutionVO {
    private ExecutionInstance instance;
    private List<ExecutionNodeLog> logs = new ArrayList<>();
    /** 与开放 API data.output 同形态的投影结果 */
    private Object publicOutput;
}
