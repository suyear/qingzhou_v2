package com.qingzhou.modules.openapi.controller;

import com.qingzhou.common.api.R;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.dto.TryRunRequest;
import com.qingzhou.modules.execution.engine.WorkflowEngine;
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

@RestController
@RequestMapping("/openapi/v1/workflows")
@RequiredArgsConstructor
public class OpenApiWorkflowController {

    private final WorkflowEngine workflowEngine;
    private final OpenapiAppService openapiAppService;

    @PostMapping("/{code}/execute")
    public R<ExecutionVO> execute(
            @PathVariable String code,
            @RequestBody(required = false) TryRunRequest request,
            HttpServletRequest httpRequest) {
        OpenapiApp app = (OpenapiApp) httpRequest.getAttribute(OpenApiAuthenticator.ATTR_APP);
        Long appId = app == null ? null : app.getId();
        openapiAppService.assertGranted(appId, code);
        return R.ok(workflowEngine.runByCode(
                code,
                "OPENAPI",
                appId,
                request == null ? null : request.getInput()));
    }
}
