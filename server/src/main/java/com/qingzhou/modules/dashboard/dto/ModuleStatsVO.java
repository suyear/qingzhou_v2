package com.qingzhou.modules.dashboard.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ModuleStatsVO {

    private ComponentStats components;
    private CredentialStats credentials;
    private WorkflowStats workflows;
    private ScheduleStats schedules;
    private ExecutionStats executions;
    private OpenapiStats openapi;
    private ActionItems actionItems;

    @Data
    @Builder
    public static class ComponentStats {
        private long total;
        private long httpCount;
        private long databaseCount;
        private long enabled;
    }

    @Data
    @Builder
    public static class CredentialStats {
        private long total;
        private Map<String, Long> byType;
        private long enabled;
    }

    @Data
    @Builder
    public static class WorkflowStats {
        private long total;
        private long draft;
        private long published;
        private long disabled;
        private long success7d;
        private long failed7d;
    }

    @Data
    @Builder
    public static class ScheduleStats {
        private long total;
        private long running;
        private long stopped;
        private long success7d;
        private long failed7d;
        private long next24h;
    }

    @Data
    @Builder
    public static class ExecutionStats {
        private long todayTotal;
        private long todaySuccess;
        private long todayFailed;
        private long todayTimeout;
        private Long todayAvgMs;
    }

    @Data
    @Builder
    public static class OpenapiStats {
        private long apps;
        private long ready;
        private long invoke7d;
        private long fail7d;
        private List<NamedCountVO> topApps;
    }

    @Data
    @Builder
    public static class ActionItems {
        private long recentFailures;
        private long stoppedSchedules;
        private String licenseStatus;
        private String licenseMessage;
        private boolean licenseExpiringSoon;
    }
}
