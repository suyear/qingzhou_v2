package com.qingzhou.modules.openapi.security;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.constant.RedisKeys;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.infra.redis.RedisOps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 幂等结果放 Redis。Redis 不可用时降级为正常执行，不阻断调用。
 */
@Slf4j
@Component
public class OpenApiIdempotencyStore {

    private final RedisOps redisOps;
    private final Duration ttl;

    public OpenApiIdempotencyStore(
            RedisOps redisOps,
            @Value("${qingzhou.openapi.idempotency-ttl-seconds:86400}") long ttlSeconds) {
        this.redisOps = redisOps;
        long seconds = ttlSeconds <= 0 ? 86400 : ttlSeconds;
        this.ttl = Duration.ofSeconds(seconds);
    }

    /**
     * @return 可直接回放的响应；未命中返回 null
     */
    public OpenApiIdempotency.Packed replay(String appKey, String method, String uri, String idemKey, String requestBody) {
        String redisKey = redisKey(appKey, method, uri, idemKey);
        String stored;
        try {
            stored = redisOps.get(redisKey);
        } catch (Exception ex) {
            log.warn("读取 OpenAPI 幂等缓存失败: {}", ex.getMessage());
            return null;
        }
        if (stored == null) {
            return null;
        }
        OpenApiIdempotency.Packed packed = OpenApiIdempotency.unpack(stored);
        if (packed == null) {
            return null;
        }
        String hash = OpenApiIdempotency.bodyHash(requestBody);
        if (!hash.equals(packed.bodyHash())) {
            throw new BizException(ResultCode.CONFLICT, "幂等键已用于不同的请求体");
        }
        return packed;
    }

    public void store(String appKey, String method, String uri, String idemKey, String requestBody,
                      int status, String responseBody) {
        if (status >= 500 || responseBody == null || responseBody.length() > OpenApiIdempotency.MAX_STORED_BODY) {
            return;
        }
        String redisKey = redisKey(appKey, method, uri, idemKey);
        String packed = OpenApiIdempotency.pack(status, OpenApiIdempotency.bodyHash(requestBody), responseBody);
        try {
            Boolean created = redisOps.setIfAbsent(redisKey, packed, ttl);
            if (!Boolean.TRUE.equals(created)) {
                log.debug("幂等键已被并发请求写入 appKey={}", appKey);
            }
        } catch (Exception ex) {
            log.warn("写入 OpenAPI 幂等缓存失败: {}", ex.getMessage());
        }
    }

    private static String redisKey(String appKey, String method, String uri, String idemKey) {
        return RedisKeys.openApiIdem(appKey, OpenApiIdempotency.fingerprint(method, uri, idemKey));
    }
}
