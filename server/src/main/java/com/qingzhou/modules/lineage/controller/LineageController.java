package com.qingzhou.modules.lineage.controller;

import com.qingzhou.common.api.R;
import com.qingzhou.modules.lineage.dto.ExecutionChainVO;
import com.qingzhou.modules.lineage.dto.LineageVO;
import com.qingzhou.modules.lineage.service.LineageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LineageController {

    private final LineageService lineageService;

    @GetMapping("/lineage/components/{id}")
    public R<LineageVO> component(@PathVariable Long id) {
        return R.ok(lineageService.componentLineage(id));
    }

    @GetMapping("/lineage/workflows/{id}")
    public R<LineageVO> workflow(@PathVariable Long id) {
        return R.ok(lineageService.workflowLineage(id));
    }

    @GetMapping("/executions/{id}/chain")
    public R<ExecutionChainVO> chain(@PathVariable Long id) {
        return R.ok(lineageService.executionChain(id));
    }
}
