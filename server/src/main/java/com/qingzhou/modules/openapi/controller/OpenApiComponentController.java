package com.qingzhou.modules.openapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.R;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.audit.service.AuditLogService;
import com.qingzhou.modules.component.dto.ComponentTestRequest;
import com.qingzhou.modules.component.dto.ComponentTestVO;
import com.qingzhou.modules.component.entity.ApiComponent;
import com.qingzhou.modules.component.service.ApiComponentService;
import com.qingzhou.modules.execution.dto.OpenApiExecuteResultVO;
import com.qingzhou.modules.execution.support.DbResultCleaner;
import com.qingzhou.modules.openapi.entity.OpenapiApp;
import com.qingzhou.modules.openapi.security.OpenApiAuthenticator;
import com.qingzhou.modules.openapi.service.OpenapiAppService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/openapi/v1/components")
@RequiredArgsConstructor
public class OpenApiComponentController {

    private final OpenapiAppService openapiAppService;
    private final ApiComponentService apiComponentService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * 开放调用接口服务：body 为组件入参扁平 JSON。
     */
    @PostMapping("/{code}/execute")
    public R<OpenApiExecuteResultVO> execute(
            @PathVariable String code,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest httpRequest) {
        OpenapiApp app = (OpenapiApp) httpRequest.getAttribute(OpenApiAuthenticator.ATTR_APP);
        Long appId = app == null ? null : app.getId();
        openapiAppService.assertComponentGranted(appId, code);

        ApiComponent component = apiComponentService.lambdaQuery()
                .eq(ApiComponent::getComponentCode, code)
                .one();
        if (component == null) {
            throw new BizException(ResultCode.NOT_FOUND, "接口服务不存在: " + code);
        }

        Map<String, Object> input = unwrapInput(body);
        ComponentTestRequest request = new ComponentTestRequest();
        request.setParams(input);
        ComponentTestVO test = apiComponentService.test(component.getId(), request);

        OpenApiExecuteResultVO result = OpenApiExecuteResultVO.builder()
                .status(test.isSuccess() ? "SUCCESS" : "FAILED")
                .durationMs(test.getDurationMs())
                .errorMsg(test.isSuccess() ? null : test.getMessage())
                .output(DbResultCleaner.stripRedundant(parseOutput(test.getResponseBody())))
                .build();

        auditLogService.record(
                null,
                app == null ? null : app.getAppKey(),
                "OPENAPI_COMPONENT_INVOKE",
                "COMPONENT",
                String.valueOf(component.getId()),
                test.isSuccess() ? "SUCCESS" : "FAILED",
                (test.isSuccess() ? "调用接口服务成功: " : "调用接口服务失败: ") + code,
                null);

        return R.ok(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapInput(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Map.of();
        }
        if (body.size() == 1 && body.containsKey("input")) {
            Object nested = body.get("input");
            if (nested == null) {
                return Map.of();
            }
            if (nested instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                return copy;
            }
        }
        return body;
    }

    private Object parseOutput(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception ignored) {
            return body;
        }
    }
}
