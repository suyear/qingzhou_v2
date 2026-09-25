package com.qingzhou.modules.dashboard.controller;

import com.qingzhou.common.api.R;
import com.qingzhou.modules.dashboard.dto.DashboardOverviewVO;
import com.qingzhou.modules.dashboard.dto.ModuleStatsVO;
import com.qingzhou.modules.dashboard.service.DashboardService;
import com.qingzhou.modules.dashboard.service.ModuleStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final ModuleStatsService moduleStatsService;

    @GetMapping("/overview")
    public R<DashboardOverviewVO> overview(@RequestParam(value = "days", required = false) Integer days) {
        return R.ok(dashboardService.overview(days));
    }

    @GetMapping("/module-stats")
    public R<ModuleStatsVO> moduleStats() {
        return R.ok(moduleStatsService.overview());
    }

    @GetMapping("/component-refs/{id}")
    public R<Long> componentRefs(@PathVariable Long id) {
        return R.ok(moduleStatsService.componentRefCount(id));
    }

    @GetMapping("/credential-refs/{id}")
    public R<Long> credentialRefs(@PathVariable Long id) {
        return R.ok(moduleStatsService.credentialRefCount(id));
    }
}
