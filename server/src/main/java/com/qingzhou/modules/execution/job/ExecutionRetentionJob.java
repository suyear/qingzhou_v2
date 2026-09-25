package com.qingzhou.modules.execution.job;

import com.qingzhou.modules.execution.service.ExecutionInstanceService;
import com.qingzhou.modules.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按系统设置的保留天数清理过期执行实例与节点日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionRetentionJob {

    private final ExecutionInstanceService executionInstanceService;
    private final SystemSettingService systemSettingService;

    /** 每天 03:30 清理一批过期数据 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void purge() {
        int days = 90;
        try {
            var settings = systemSettingService.get();
            if (settings.getExecutionRetentionDays() != null) {
                days = settings.getExecutionRetentionDays();
            }
        } catch (Exception ex) {
            log.warn("读取执行保留天数失败，使用默认 90: {}", ex.getMessage());
        }
        int total = 0;
        for (int i = 0; i < 20; i++) {
            int deleted = executionInstanceService.purgeExpired(days);
            total += deleted;
            if (deleted == 0) {
                break;
            }
        }
        if (total > 0) {
            log.info("执行日志清理完成 retentionDays={} deletedInstances={}", days, total);
        }
    }
}
