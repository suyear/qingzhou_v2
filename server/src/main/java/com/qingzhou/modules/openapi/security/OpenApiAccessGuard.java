package com.qingzhou.modules.openapi.security;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.constant.RedisKeys;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.infra.redis.RedisOps;
import com.qingzhou.modules.openapi.entity.OpenapiApp;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OpenApiAccessGuard {

    private final RedisOps redisOps;

    public void check(OpenapiApp app, HttpServletRequest request) {
        String clientIp = clientIp(request);
        if (!IpRules.isAllowed(clientIp, app.getIpWhitelist())) {
            throw new BizException(ResultCode.FORBIDDEN, "IP 不在白名单: " + clientIp);
        }
        int qps = app.getRateLimitQps() == null ? 0 : app.getRateLimitQps();
        if (qps <= 0) {
            return;
        }
        long window = Instant.now().getEpochSecond();
        long count = redisOps.increment(RedisKeys.openApiQps(app.getAppKey(), window), Duration.ofSeconds(2));
        if (count > qps) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "超过应用 QPS 限制 " + qps);
        }
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return IpRules.normalize(forwarded.split(",")[0]);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return IpRules.normalize(realIp);
        }
        return IpRules.normalize(request.getRemoteAddr());
    }
}
