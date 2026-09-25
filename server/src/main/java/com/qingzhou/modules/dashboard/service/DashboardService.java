package com.qingzhou.modules.dashboard.service;

import com.qingzhou.modules.dashboard.dto.DashboardOverviewVO;

public interface DashboardService {

    DashboardOverviewVO overview(Integer days);
}
