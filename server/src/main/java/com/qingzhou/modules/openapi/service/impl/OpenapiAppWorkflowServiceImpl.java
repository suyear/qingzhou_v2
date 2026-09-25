package com.qingzhou.modules.openapi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingzhou.modules.openapi.entity.OpenapiAppWorkflow;
import com.qingzhou.modules.openapi.mapper.OpenapiAppWorkflowMapper;
import com.qingzhou.modules.openapi.service.OpenapiAppWorkflowService;
import org.springframework.stereotype.Service;

@Service
public class OpenapiAppWorkflowServiceImpl extends ServiceImpl<OpenapiAppWorkflowMapper, OpenapiAppWorkflow>
        implements OpenapiAppWorkflowService {
}
