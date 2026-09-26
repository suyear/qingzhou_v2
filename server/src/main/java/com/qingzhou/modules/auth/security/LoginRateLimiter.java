package com.qingzhou.modules.auth.security;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.constant.RedisKeys;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.infra.redis.RedisOps;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 登录失败限流：按 IP + 用户名计数，超限后短暂锁定。
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final RedisOps redisOps;

    @Value("${qingzhou.auth.login-max-failures:8}")
    private int maxFailures;

    @Value("${qingzhou.auth.login-lock-seconds:300}")
    private long lockSeconds;

    public void assertAllowed(String username, String clientIp) {
        if (maxFailures <= 0) {
            return;
        }
        String key = RedisKeys.loginFail(safe(username), safe(clientIp));
        String raw = redisOps.get(key);
        if (!StringUtils.hasText(raw)) {
            return;
        }
        try {
            if (Long.parseLong(raw) >= maxFailures) {
                throw new BizException(ResultCode.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后再试");
            }
        } catch (NumberFormatException ignored) {
            // ignore corrupt counter
        }
    }

    public void onFailure(String username, String clientIp) {
        if (maxFailures <= 0) {
            return;
        }
        String key = RedisKeys.loginFail(safe(username), safe(clientIp));
        redisOps.increment(key, Duration.ofSeconds(Math.max(60, lockSeconds)));
    }

    public void onSuccess(String username, String clientIp) {
        redisOps.delete(RedisKeys.loginFail(safe(username), safe(clientIp)));
    }

    private static String safe(String value) {
        if (!StringUtils.hasText(value)) {
            return "_";
        }
        return value.trim().toLowerCase().replaceAll("[^a-z0-9._\\-:@]", "_");
    }
}
