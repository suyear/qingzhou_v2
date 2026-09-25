package com.qingzhou.modules.dashboard.service.impl;

import com.qingzhou.modules.dashboard.dto.ModuleStatsVO;
import com.qingzhou.modules.dashboard.dto.NamedCountVO;
import com.qingzhou.modules.dashboard.mapper.ModuleStatsMapper;
import com.qingzhou.modules.dashboard.service.ModuleStatsService;
import com.qingzhou.modules.license.dto.LicenseStatusVO;
import com.qingzhou.modules.license.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ModuleStatsServiceImpl implements ModuleStatsService {

    private final ModuleStatsMapper statsMapper;
    private final LicenseService licenseService;

    @Override
    public ModuleStatsVO overview() {
        LocalDateTime since7d = LocalDateTime.now().minusDays(7);
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = LocalDate.now().plusDays(1).atStartOfDay();

        long totalComp = statsMapper.countComponents();
        long dbComp = statsMapper.countDatabaseComponents();

        Map<String, Long> credTypes = new HashMap<>();
        for (NamedCountVO item : statsMapper.countCredentialsByType()) {
            credTypes.put(item.getName(), item.getCount() == null ? 0L : item.getCount());
        }

        Map<String, Object> wfOut = nullToEmpty(statsMapper.executionOutcomeSince(since7d));
        Map<String, Object> schOut = nullToEmpty(statsMapper.scheduleOutcomeSince(since7d));
        Map<String, Object> today = nullToEmpty(statsMapper.executionToday(dayStart, dayEnd));

        LicenseStatusVO license = licenseService.status();
        boolean expiringSoon = license.getExpiresAt() != null
                && !license.getExpiresAt().isBefore(LocalDate.now())
                && !license.getExpiresAt().isAfter(LocalDate.now().plusDays(30));

        return ModuleStatsVO.builder()
                .components(ModuleStatsVO.ComponentStats.builder()
                        .total(totalComp)
                        .databaseCount(dbComp)
                        .httpCount(Math.max(0, totalComp - dbComp))
                        .enabled(statsMapper.countEnabledComponents())
                        .build())
                .credentials(ModuleStatsVO.CredentialStats.builder()
                        .total(statsMapper.countCredentials())
                        .enabled(statsMapper.countEnabledCredentials())
                        .byType(credTypes)
                        .build())
                .workflows(ModuleStatsVO.WorkflowStats.builder()
                        .total(statsMapper.countWorkflows())
                        .draft(statsMapper.countWorkflowsByStatus("DRAFT"))
                        .published(statsMapper.countWorkflowsByStatus("PUBLISHED"))
                        .disabled(statsMapper.countWorkflowsByStatus("DISABLED"))
                        .success7d(asLong(wfOut.get("success_count")))
                        .failed7d(asLong(wfOut.get("failed_count")))
                        .build())
                .schedules(ModuleStatsVO.ScheduleStats.builder()
                        .total(statsMapper.countSchedules())
                        .running(statsMapper.countSchedulesByStatus(1))
                        .stopped(statsMapper.countSchedulesByStatus(0))
                        .success7d(asLong(schOut.get("success_count")))
                        .failed7d(asLong(schOut.get("failed_count")))
                        .next24h(statsMapper.countSchedulesNext24h())
                        .build())
                .executions(ModuleStatsVO.ExecutionStats.builder()
                        .todayTotal(asLong(today.get("total")))
                        .todaySuccess(asLong(today.get("success_count")))
                        .todayFailed(asLong(today.get("failed_count")))
                        .todayTimeout(asLong(today.get("timeout_count")))
                        .todayAvgMs(today.get("avg_ms") == null ? null : asLong(today.get("avg_ms")))
                        .build())
                .openapi(ModuleStatsVO.OpenapiStats.builder()
                        .apps(statsMapper.countOpenapiApps())
                        .ready(statsMapper.countReadyOpenapiApps())
                        .invoke7d(statsMapper.countOpenapiInvokes(since7d))
                        .fail7d(statsMapper.countOpenapiFails(since7d))
                        .topApps(List.of())
                        .build())
                .actionItems(ModuleStatsVO.ActionItems.builder()
                        .recentFailures(statsMapper.countRecentFailures(since7d))
                        .stoppedSchedules(statsMapper.countSchedulesByStatus(0))
                        .licenseStatus(license.getStatus())
                        .licenseMessage(license.getMessage())
                        .licenseExpiringSoon(expiringSoon)
                        .build())
                .build();
    }

    @Override
    public long componentRefCount(Long componentId) {
        return statsMapper.countWorkflowsReferencingComponent(componentId);
    }

    @Override
    public long credentialRefCount(Long credentialId) {
        return statsMapper.countWorkflowsReferencingCredential(credentialId);
    }

    private static Map<String, Object> nullToEmpty(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
