package com.qingzhou.modules.openapi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingzhou.modules.openapi.entity.OpenapiAppComponent;
import com.qingzhou.modules.openapi.mapper.OpenapiAppComponentMapper;
import com.qingzhou.modules.openapi.service.OpenapiAppComponentService;
import org.springframework.stereotype.Service;

@Service
public class OpenapiAppComponentServiceImpl
        extends ServiceImpl<OpenapiAppComponentMapper, OpenapiAppComponent>
        implements OpenapiAppComponentService {
}
