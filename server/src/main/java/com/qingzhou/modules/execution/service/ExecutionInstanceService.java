package com.qingzhou.modules.execution.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qingzhou.modules.execution.dto.ExecutionListVO;
import com.qingzhou.modules.execution.dto.ExecutionQuery;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.entity.ExecutionInstance;

public interface ExecutionInstanceService extends IService<ExecutionInstance> {

    IPage<ExecutionListVO> pageExecutions(ExecutionQuery query);

    ExecutionVO detail(Long id);
}
