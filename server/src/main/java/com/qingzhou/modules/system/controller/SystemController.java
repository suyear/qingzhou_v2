package com.qingzhou.modules.system.controller;

import com.qingzhou.common.api.R;
import com.qingzhou.modules.system.dto.SystemSettingsVO;
import com.qingzhou.modules.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemSettingService settingService;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping("/settings")
    public R<SystemSettingsVO> settings() {
        return R.ok(settingService.get());
    }

    @PutMapping("/settings")
    public R<SystemSettingsVO> save(@RequestBody SystemSettingsVO request) {
        return R.ok(settingService.save(request));
    }

    @GetMapping("/health-detail")
    public R<Map<String, Object>> healthDetail() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mysql", pingMysql());
        map.put("redis", pingRedis());
        return R.ok(map);
    }

    private Map<String, Object> pingMysql() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Map.of("ok", true, "message", "连接正常");
        } catch (Exception ex) {
            return Map.of("ok", false, "message", ex.getMessage());
        }
    }

    private Map<String, Object> pingRedis() {
        try {
            String pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
            return Map.of("ok", true, "message", pong == null ? "PONG" : pong);
        } catch (Exception ex) {
            return Map.of("ok", false, "message", ex.getMessage());
        }
    }
}
