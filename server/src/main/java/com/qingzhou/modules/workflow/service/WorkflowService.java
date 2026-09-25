package com.qingzhou.modules.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingzhou.modules.workflow.dto.WorkflowSaveRequest;
import com.qingzhou.modules.workflow.dto.WorkflowVO;
import com.qingzhou.modules.workflow.entity.Workflow;

public interface WorkflowService extends IService<Workflow> {

    WorkflowVO create(WorkflowSaveRequest request);

    WorkflowVO update(Long id, WorkflowSaveRequest request);

    WorkflowVO detail(Long id);

    WorkflowVO publish(Long id);

    WorkflowVO disable(Long id);
}
