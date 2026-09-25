package com.qingzhou.modules.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.R;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.dto.TryRunRequest;
import com.qingzhou.modules.execution.engine.WorkflowEngine;
import com.qingzhou.modules.workflow.dto.WorkflowSaveRequest;
import com.qingzhou.modules.workflow.dto.WorkflowVO;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowEngine workflowEngine;

    @GetMapping
    public R<IPage<Workflow>> page(PageQuery query, @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(Workflow::getWorkflowName, query.getKeyword())
                        .or()
                        .like(Workflow::getWorkflowCode, query.getKeyword()))
                .eq(StringUtils.hasText(status), Workflow::getStatus, status)
                .orderByDesc(Workflow::getUpdateTime);
        return R.ok(workflowService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper));
    }

    @GetMapping("/{id}")
    public R<WorkflowVO> detail(@PathVariable Long id) {
        return R.ok(workflowService.detail(id));
    }

    @PostMapping
    public R<WorkflowVO> create(@Valid @RequestBody WorkflowSaveRequest request) {
        return R.ok(workflowService.create(request));
    }

    @PutMapping("/{id}")
    public R<WorkflowVO> update(@PathVariable Long id, @Valid @RequestBody WorkflowSaveRequest request) {
        return R.ok(workflowService.update(id, request));
    }

    @PostMapping("/{id}/try-run")
    public R<ExecutionVO> tryRun(@PathVariable Long id, @RequestBody(required = false) TryRunRequest request) {
        return R.ok(workflowEngine.tryRun(id, request == null ? null : request.getInput()));
    }

    @PostMapping("/{id}/publish")
    public R<WorkflowVO> publish(@PathVariable Long id) {
        return R.ok(workflowService.publish(id));
    }

    @PostMapping("/{id}/disable")
    public R<WorkflowVO> disable(@PathVariable Long id) {
        return R.ok(workflowService.disable(id));
    }
}
