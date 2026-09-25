package com.qingzhou.modules.dashboard.service;

import com.qingzhou.modules.dashboard.dto.ModuleStatsVO;

public interface ModuleStatsService {

    ModuleStatsVO overview();

    long componentRefCount(Long componentId);

    long credentialRefCount(Long credentialId);
}
