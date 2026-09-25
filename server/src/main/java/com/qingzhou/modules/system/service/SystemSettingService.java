package com.qingzhou.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingzhou.modules.audit.service.AuditLogService;
import com.qingzhou.modules.auth.security.AuthContext;
import com.qingzhou.modules.system.dto.SystemSettingsVO;
import com.qingzhou.modules.system.entity.SystemSetting;
import com.qingzhou.modules.system.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingMapper settingMapper;
    private final AuditLogService auditLogService;

    @Value("${qingzhou.openapi.public-base-url:}")
    private String configuredPublicBaseUrl;

    public SystemSettingsVO get() {
        SystemSettingsVO vo = new SystemSettingsVO();
        vo.setSiteName(getValue("site_name", "轻舟"));
        String base = getValue("openapi_public_base_url", "");
        if (!StringUtils.hasText(base)) {
            base = configuredPublicBaseUrl == null ? "" : configuredPublicBaseUrl;
        }
        vo.setOpenapiPublicBaseUrl(base);
        String days = getValue("execution_retention_days", "90");
        try {
            vo.setExecutionRetentionDays(Integer.parseInt(days));
        } catch (NumberFormatException ex) {
            vo.setExecutionRetentionDays(90);
        }
        vo.setTimezone(getValue("timezone", "Asia/Shanghai"));
        return vo;
    }

    @Transactional
    public SystemSettingsVO save(SystemSettingsVO request) {
        put("site_name", request.getSiteName());
        put("openapi_public_base_url", request.getOpenapiPublicBaseUrl());
        put("execution_retention_days", String.valueOf(
                request.getExecutionRetentionDays() == null ? 90 : request.getExecutionRetentionDays()));
        put("timezone", request.getTimezone());
        auditLogService.recordCurrent("SYSTEM_SETTINGS_UPDATE", "SYSTEM", null, "SUCCESS", "更新系统设置");
        return get();
    }

    public String getValue(String key, String defaultValue) {
        SystemSetting row = settingMapper.selectOne(new LambdaQueryWrapper<SystemSetting>()
                .eq(SystemSetting::getSettingKey, key)
                .last("LIMIT 1"));
        if (row == null || row.getSettingValue() == null) {
            return defaultValue;
        }
        return row.getSettingValue();
    }

    private void put(String key, String value) {
        SystemSetting row = settingMapper.selectOne(new LambdaQueryWrapper<SystemSetting>()
                .eq(SystemSetting::getSettingKey, key)
                .last("LIMIT 1"));
        if (row == null) {
            row = new SystemSetting();
            row.setSettingKey(key);
            row.setSettingValue(value == null ? "" : value);
            row.setUpdateBy(AuthContext.currentUsername());
            settingMapper.insert(row);
        } else {
            row.setSettingValue(value == null ? "" : value);
            row.setUpdateBy(AuthContext.currentUsername());
            settingMapper.updateById(row);
        }
    }
}
