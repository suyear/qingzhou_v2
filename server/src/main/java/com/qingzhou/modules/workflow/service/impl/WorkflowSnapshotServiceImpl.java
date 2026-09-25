package com.qingzhou.modules.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingzhou.modules.workflow.entity.WorkflowSnapshot;
import com.qingzhou.modules.workflow.mapper.WorkflowSnapshotMapper;
import com.qingzhou.modules.workflow.service.WorkflowSnapshotService;
import org.springframework.stereotype.Service;

@Service
public class WorkflowSnapshotServiceImpl extends ServiceImpl<WorkflowSnapshotMapper, WorkflowSnapshot>
        implements WorkflowSnapshotService {
}
