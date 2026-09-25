package com.qingzhou.modules.execution.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import com.qingzhou.modules.execution.mapper.ExecutionNodeLogMapper;
import com.qingzhou.modules.execution.service.ExecutionNodeLogService;
import org.springframework.stereotype.Service;

@Service
public class ExecutionNodeLogServiceImpl extends ServiceImpl<ExecutionNodeLogMapper, ExecutionNodeLog>
        implements ExecutionNodeLogService {
}
