package com.qingzhou.modules.openapi.security;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.constant.RedisKeys;
import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.crypto.SignatureUtil;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.infra.redis.RedisOps;
import com.qingzhou.modules.openapi.entity.OpenapiApp;
import com.qingzhou.modules.openapi.service.OpenapiAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OpenApiAuthenticator {

    public static final String ATTR_APP = "OPENAPI_APP";

    private final OpenapiAppService openapiAppService;
    private final AesEncryptor aesEncryptor;
    private final RedisOps redisOps;

    @Value("${qingzhou.openapi.nonce-ttl-seconds:600}")
    private long nonceTtlSeconds;

    public OpenapiApp authenticate(String appKey, String timestamp, String nonce, String signature, String body) {
        if (!StringUtils.hasText(appKey)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少 " + SignatureUtil.HEADER_APP_KEY);
        }
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少签名头 X-Timestamp / X-Nonce / X-Signature");
        }
        OpenapiApp app = openapiAppService.lambdaQuery()
                .eq(OpenapiApp::getAppKey, appKey)
                .one();
        if (app == null || !Integer.valueOf(1).equals(app.getStatus())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "无效的 API Key");
        }
        if (app.getExpireTime() != null && app.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "应用已过期");
        }
        String secret = aesEncryptor.decrypt(app.getAppSecretCipher());
        SignatureUtil.verifyOrThrow(body, timestamp, nonce, secret, signature);

        String nonceKey = RedisKeys.openApiNonce(appKey, nonce);
        boolean firstSeen = Boolean.TRUE.equals(redisOps.setIfAbsent(nonceKey, "1", Duration.ofSeconds(nonceTtlSeconds)));
        if (!firstSeen) {
            throw new BizException(ResultCode.UNAUTHORIZED, "Nonce 重复，疑似重放攻击");
        }
        return app;
    }
}
