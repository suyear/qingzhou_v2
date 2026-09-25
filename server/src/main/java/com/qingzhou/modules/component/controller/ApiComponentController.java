package com.qingzhou.modules.component.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.R;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.component.dto.ComponentSaveRequest;
import com.qingzhou.modules.component.dto.ComponentTestRequest;
import com.qingzhou.modules.component.dto.ComponentTestVO;
import com.qingzhou.modules.component.entity.ApiComponent;
import com.qingzhou.modules.component.service.ApiComponentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/components")
@RequiredArgsConstructor
public class ApiComponentController {

    private final ApiComponentService apiComponentService;

    @GetMapping
    public R<IPage<ApiComponent>> page(PageQuery query,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String provider,
                                       @RequestParam(required = false) Integer isPreset,
                                       @RequestParam(required = false) String httpMethod) {
        LambdaQueryWrapper<ApiComponent> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(ApiComponent::getComponentName, query.getKeyword())
                        .or()
                        .like(ApiComponent::getComponentCode, query.getKeyword())
                        .or()
                        .like(ApiComponent::getUrlTemplate, query.getKeyword()))
                .eq(StringUtils.hasText(category), ApiComponent::getCategory, category)
                .eq(StringUtils.hasText(provider), ApiComponent::getProvider, provider)
                .eq(isPreset != null, ApiComponent::getIsPreset, isPreset)
                .eq(StringUtils.hasText(httpMethod), ApiComponent::getHttpMethod, httpMethod)
                .orderByDesc(ApiComponent::getUpdateTime);
        return R.ok(apiComponentService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper));
    }

    @GetMapping("/{id}")
    public R<ApiComponent> detail(@PathVariable Long id) {
        ApiComponent component = apiComponentService.getById(id);
        if (component == null) {
            throw new BizException(ResultCode.NOT_FOUND, "接口组件不存在");
        }
        return R.ok(component);
    }

    @PostMapping
    public R<ApiComponent> create(@Valid @RequestBody ComponentSaveRequest request) {
        return R.ok(apiComponentService.create(request));
    }

    @PutMapping("/{id}")
    public R<ApiComponent> update(@PathVariable Long id, @Valid @RequestBody ComponentSaveRequest request) {
        return R.ok(apiComponentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        apiComponentService.deleteById(id);
        return R.ok();
    }

    @PostMapping("/{id}/test")
    public R<ComponentTestVO> test(@PathVariable Long id, @RequestBody(required = false) ComponentTestRequest request) {
        return R.ok(apiComponentService.test(id, request == null ? new ComponentTestRequest() : request));
    }
}
