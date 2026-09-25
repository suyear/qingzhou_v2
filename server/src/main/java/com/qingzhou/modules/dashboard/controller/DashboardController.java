package com.qingzhou.modules.dashboard.controller;

import com.qingzhou.common.api.R;
import com.qingzhou.modules.dashboard.dto.DashboardOverviewVO;
import com.qingzhou.modules.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public R<DashboardOverviewVO> overview(@RequestParam(value = "days", required = false) Integer days) {
        return R.ok(dashboardService.overview(days));
    }
}
