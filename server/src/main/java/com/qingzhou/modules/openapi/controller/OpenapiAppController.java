package com.qingzhou.modules.openapi.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.R;
import com.qingzhou.modules.openapi.dto.OpenapiAppBindRequest;
import com.qingzhou.modules.openapi.dto.OpenapiAppCreateRequest;
import com.qingzhou.modules.openapi.dto.OpenapiAppCreatedVO;
import com.qingzhou.modules.openapi.dto.OpenapiAppListVO;
import com.qingzhou.modules.openapi.dto.OpenapiAppUpdateRequest;
import com.qingzhou.modules.openapi.dto.OpenapiInvokePreviewVO;
import com.qingzhou.modules.openapi.dto.OpenapiInvokeRequest;
import com.qingzhou.modules.openapi.dto.OpenapiInvokeResultVO;
import com.qingzhou.modules.openapi.entity.OpenapiApp;
import com.qingzhou.modules.openapi.service.OpenApiConsoleService;
import com.qingzhou.modules.openapi.service.OpenapiAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/openapi/apps")
@RequiredArgsConstructor
public class OpenapiAppController {

    private final OpenapiAppService openapiAppService;
    private final OpenApiConsoleService openApiConsoleService;

    @GetMapping
    public R<IPage<OpenapiAppListVO>> page(PageQuery query) {
        return R.ok(openapiAppService.pageApps(query));
    }

    @PostMapping
    public R<OpenapiAppCreatedVO> create(@Valid @RequestBody OpenapiAppCreateRequest request) {
        return R.ok(openapiAppService.createApp(request));
    }

    @PutMapping("/{id}")
    public R<OpenapiApp> update(@PathVariable Long id, @Valid @RequestBody OpenapiAppUpdateRequest request) {
        return R.ok(openapiAppService.updateApp(id, request));
    }

    @PostMapping("/{id}/reset-secret")
    public R<OpenapiAppCreatedVO> resetSecret(@PathVariable Long id) {
        return R.ok(openapiAppService.resetSecret(id));
    }

    @GetMapping("/{id}/workflows")
    public R<List<Long>> granted(@PathVariable Long id) {
        return R.ok(openapiAppService.listGrantedWorkflowIds(id));
    }

    @PutMapping("/{id}/workflows")
    public R<Void> bind(@PathVariable Long id, @Valid @RequestBody OpenapiAppBindRequest request) {
        openapiAppService.bindWorkflows(id, request);
        return R.ok();
    }

    @PostMapping("/{id}/invoke-preview")
    public R<OpenapiInvokePreviewVO> invokePreview(@PathVariable Long id, @Valid @RequestBody OpenapiInvokeRequest request) {
        return R.ok(openApiConsoleService.preview(id, request));
    }

    @PostMapping("/{id}/invoke")
    public R<OpenapiInvokeResultVO> invoke(@PathVariable Long id, @Valid @RequestBody OpenapiInvokeRequest request) {
        return R.ok(openApiConsoleService.invoke(id, request));
    }
}
