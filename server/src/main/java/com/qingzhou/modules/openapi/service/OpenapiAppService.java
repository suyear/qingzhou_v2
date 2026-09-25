package com.qingzhou.modules.openapi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.modules.openapi.dto.OpenapiAppBindRequest;
import com.qingzhou.modules.openapi.dto.OpenapiAppCreateRequest;
import com.qingzhou.modules.openapi.dto.OpenapiAppCreatedVO;
import com.qingzhou.modules.openapi.dto.OpenapiAppListVO;
import com.qingzhou.modules.openapi.dto.OpenapiAppUpdateRequest;
import com.qingzhou.modules.openapi.entity.OpenapiApp;

import java.util.List;

public interface OpenapiAppService extends IService<OpenapiApp> {

    IPage<OpenapiAppListVO> pageApps(PageQuery query);

    OpenapiAppCreatedVO createApp(OpenapiAppCreateRequest request);

    OpenapiApp updateApp(Long id, OpenapiAppUpdateRequest request);

    OpenapiAppCreatedVO resetSecret(Long id);

    List<Long> listGrantedWorkflowIds(Long appId);

    void bindWorkflows(Long appId, OpenapiAppBindRequest request);

    void assertGranted(Long appId, String workflowCode);
}
