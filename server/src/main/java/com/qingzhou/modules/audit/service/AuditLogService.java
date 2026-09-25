package com.qingzhou.modules.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.modules.audit.entity.AuditLog;
import com.qingzhou.modules.audit.mapper.AuditLogMapper;
import com.qingzhou.modules.auth.security.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public void recordCurrent(String action, String resourceType, String resourceId, String result, String summary) {
        AuthContext.current().ifPresentOrElse(
                p -> record(p.getId(), p.getUsername(), action, resourceType, resourceId, result, summary, null),
                () -> record(null, null, action, resourceType, resourceId, result, summary, null));
    }

    public void record(Long actorId, String actorName, String action, String resourceType, String resourceId,
                       String result, String summary, String clientIp) {
        try {
            AuditLog logRow = new AuditLog();
            logRow.setActorId(actorId);
            logRow.setActorName(actorName);
            logRow.setAction(action);
            logRow.setResourceType(resourceType);
            logRow.setResourceId(resourceId);
            logRow.setResult(result == null ? "SUCCESS" : result);
            logRow.setSummary(summary);
            logRow.setClientIp(clientIp);
            logRow.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(logRow);
        } catch (Exception ex) {
            log.warn("写入审计日志失败: {}", ex.getMessage());
        }
    }

    public IPage<AuditLog> page(PageQuery query, String action, String actorName) {
        return auditLogMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()),
                new LambdaQueryWrapper<AuditLog>()
                        .eq(StringUtils.hasText(action), AuditLog::getAction, action)
                        .like(StringUtils.hasText(actorName), AuditLog::getActorName, actorName)
                        .like(StringUtils.hasText(query.getKeyword()), AuditLog::getSummary, query.getKeyword())
                        .orderByDesc(AuditLog::getId));
    }
}
