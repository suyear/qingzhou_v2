package com.qingzhou.modules.audit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.R;
import com.qingzhou.modules.audit.entity.AuditLog;
import com.qingzhou.modules.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public R<IPage<AuditLog>> page(
            PageQuery query,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorName) {
        return R.ok(auditLogService.page(query, action, actorName));
    }
}
