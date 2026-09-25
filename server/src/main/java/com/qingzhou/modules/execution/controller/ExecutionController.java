package com.qingzhou.modules.execution.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qingzhou.common.api.R;
import com.qingzhou.modules.execution.dto.ExecutionListVO;
import com.qingzhou.modules.execution.dto.ExecutionQuery;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.dto.TryRunRequest;
import com.qingzhou.modules.execution.engine.WorkflowEngine;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import com.qingzhou.modules.execution.service.ExecutionInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionInstanceService executionInstanceService;
    private final WorkflowEngine workflowEngine;

    @GetMapping
    public R<IPage<ExecutionListVO>> page(ExecutionQuery query) {
        return R.ok(executionInstanceService.pageExecutions(query));
    }

    @GetMapping("/{id}")
    public R<ExecutionVO> detail(@PathVariable Long id) {
        return R.ok(executionInstanceService.detail(id));
    }

    @GetMapping("/{id}/logs")
    public R<List<ExecutionNodeLog>> logs(@PathVariable Long id) {
        return R.ok(executionInstanceService.detail(id).getLogs());
    }

    @PostMapping("/{id}/replay")
    public R<ExecutionVO> replay(@PathVariable Long id, @RequestBody(required = false) TryRunRequest request) {
        return R.ok(workflowEngine.replay(id, request == null ? null : request.getInput()));
    }
}
