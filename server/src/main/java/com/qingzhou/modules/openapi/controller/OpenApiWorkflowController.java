package com.qingzhou.modules.openapi.controller;

import com.qingzhou.common.api.R;
import com.qingzhou.modules.execution.dto.RunResultVO;
import com.qingzhou.modules.execution.engine.WorkflowEngine;
import com.qingzhou.modules.execution.support.ExecutionResultAssembler;
import com.qingzhou.modules.openapi.entity.OpenapiApp;
import com.qingzhou.modules.openapi.security.OpenApiAuthenticator;
import com.qingzhou.modules.openapi.service.OpenapiAppService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/openapi/v1/workflows")
@RequiredArgsConstructor
public class OpenApiWorkflowController {

    private final WorkflowEngine workflowEngine;
    private final OpenapiAppService openapiAppService;
    private final ExecutionResultAssembler executionResultAssembler;

    /**
     * 开放调用：body 为工作流入参扁平 JSON（与文档一致），如 {@code {"id":"1"}}。
     * 兼容旧格式 {@code {"input":{...}}}。
     * 返回与编排试跑一致的精简结果。
     */
    @PostMapping("/{code}/execute")
    public R<RunResultVO> execute(
            @PathVariable String code,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest httpRequest) {
        OpenapiApp app = (OpenapiApp) httpRequest.getAttribute(OpenApiAuthenticator.ATTR_APP);
        Long appId = app == null ? null : app.getId();
        openapiAppService.assertGranted(appId, code);
        return R.ok(executionResultAssembler.from(
                workflowEngine.runByCode(code, "OPENAPI", appId, unwrapInput(body))));
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
                return (Map<String, Object>) map;
            }
        }
        return body;
    }
}
