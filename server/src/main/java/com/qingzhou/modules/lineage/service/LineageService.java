package com.qingzhou.modules.lineage.service;

import com.qingzhou.modules.lineage.dto.ExecutionChainVO;
import com.qingzhou.modules.lineage.dto.LineageVO;

public interface LineageService {

    LineageVO componentLineage(Long componentId);

    LineageVO workflowLineage(Long workflowId);

    ExecutionChainVO executionChain(Long executionId);
}
