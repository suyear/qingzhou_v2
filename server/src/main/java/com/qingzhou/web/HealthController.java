package com.qingzhou.web;

import com.qingzhou.common.api.R;
import com.qingzhou.infra.redis.RedisOps;
import com.qingzhou.modules.component.service.ApiComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisOps redisOps;
    private final ApiComponentService apiComponentService;

    @GetMapping
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "qingzhou-server");
        data.put("time", LocalDateTime.now());
        data.put("mysql", pingMysql());
        data.put("redis", pingRedis());
        data.put("presetComponentCount", apiComponentService.count());
        return R.ok(data);
    }

    private String pingMysql() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(one) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String pingRedis() {
        try {
            return redisOps.ping() ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
