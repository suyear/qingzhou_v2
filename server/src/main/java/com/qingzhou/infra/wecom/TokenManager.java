package com.qingzhou.infra.wecom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.constant.RedisKeys;
import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.infra.redis.RedisOps;
import com.qingzhou.modules.credential.entity.Credential;
import com.qingzhou.modules.credential.service.CredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

/**
 * 企业微信 AccessToken 缓存与刷新。
 *
 * 流程：
 * 1. 先读 Redis，命中直接返回
 * 2. 未命中则抢分布式锁，防止多实例同时打 gettoken（企业微信有频率限制）
 * 3. 抢到锁后二次检查缓存（double-check），没有再调企业微信
 * 4. 写入 Redis，TTL = expires_in - 偏移（默认提前 5 分钟过期，避免临界失效）
 *
 * 凭证支持 GLOBAL 共享，或按工作流 INDEPENDENT 独立配置。
 */
@Slf4j
@Component
public class TokenManager {

    private static final String TYPE_WECOM = "WECOM";
    private static final String SCOPE_GLOBAL = "GLOBAL";
    private static final String SCOPE_WORKFLOW = "WORKFLOW";

    private final RedisOps redisOps;
    private final CredentialService credentialService;
    private final AesEncryptor aesEncryptor;
    private final WecomTokenClient wecomTokenClient;
    private final int expireSkewSeconds;
    private final Duration lockTtl;
    private final int lockRetry;
    private final Duration lockWait;

    public TokenManager(
            RedisOps redisOps,
            CredentialService credentialService,
            AesEncryptor aesEncryptor,
            WecomTokenClient wecomTokenClient,
            @Value("${qingzhou.wecom.token-expire-skew-seconds:300}") int expireSkewSeconds,
            @Value("${qingzhou.wecom.lock-ttl-seconds:10}") int lockTtlSeconds,
            @Value("${qingzhou.wecom.lock-retry:8}") int lockRetry,
            @Value("${qingzhou.wecom.lock-wait-ms:200}") long lockWaitMs) {
        this.redisOps = redisOps;
        this.credentialService = credentialService;
        this.aesEncryptor = aesEncryptor;
        this.wecomTokenClient = wecomTokenClient;
        this.expireSkewSeconds = expireSkewSeconds;
        this.lockTtl = Duration.ofSeconds(lockTtlSeconds);
        this.lockRetry = lockRetry;
        this.lockWait = Duration.ofMillis(lockWaitMs);
    }

    public String getAccessToken(Long credentialId) {
        return getAccessToken(loadEnabled(credentialId));
    }

    public String getGlobalAccessToken() {
        Credential credential = findGlobalCredential();
        if (credential == null) {
            throw new BizException(ResultCode.NOT_FOUND, "未配置全局企业微信凭证");
        }
        return getAccessToken(credential);
    }

    /**
     * 工作流取 Token：优先独立凭证，没有则回落到全局凭证。
     */
    public String getAccessTokenForWorkflow(Long workflowId) {
        Credential credential = findWorkflowCredential(workflowId);
        if (credential == null) {
            credential = findGlobalCredential();
        }
        if (credential == null) {
            throw new BizException(ResultCode.NOT_FOUND, "未配置企业微信凭证（工作流独立或全局）");
        }
        return getAccessToken(credential);
    }

    public String getAccessToken(Credential credential) {
        validateWecom(credential);
        String cacheKey = cacheKey(credential);
        String cached = redisOps.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            return cached;
        }

        String lockKey = RedisKeys.wecomTokenLock(credential.getId());
        String lockToken = UUID.randomUUID().toString();
        boolean locked = redisOps.tryLock(lockKey, lockToken, lockTtl, lockRetry, lockWait);
        if (!locked) {
            cached = redisOps.get(cacheKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            throw new BizException(ResultCode.THIRD_PARTY_ERROR, "获取企业微信 Token 繁忙，请稍后重试");
        }

        try {
            cached = redisOps.get(cacheKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            return refreshAndCache(credential, cacheKey);
        } finally {
            redisOps.unlock(lockKey, lockToken);
        }
    }

    public void evict(Long credentialId) {
        if (credentialId == null) {
            return;
        }
        Credential credential = credentialService.getById(credentialId);
        if (credential == null) {
            redisOps.delete(RedisKeys.wecomToken(credentialId));
            return;
        }
        redisOps.delete(cacheKey(credential));
    }

    private String refreshAndCache(Credential credential, String cacheKey) {
        String corpSecret = aesEncryptor.decrypt(credential.getSecretCipher());
        if (!StringUtils.hasText(credential.getCorpId()) || !StringUtils.hasText(corpSecret)) {
            throw new BizException(ResultCode.BAD_REQUEST, "企业微信凭证缺少 CorpId 或 Secret");
        }

        WecomTokenResponse response = wecomTokenClient.fetch(credential.getCorpId(), corpSecret);
        if (!response.success() || !StringUtils.hasText(response.accessToken())) {
            throw new BizException(ResultCode.THIRD_PARTY_ERROR,
                    "企业微信 gettoken 失败: errcode=" + response.errcode() + ", errmsg=" + response.errmsg());
        }

        int expiresIn = response.expiresIn() == null ? 7200 : response.expiresIn();
        int ttlSeconds = Math.max(60, expiresIn - expireSkewSeconds);
        redisOps.set(cacheKey, response.accessToken(), Duration.ofSeconds(ttlSeconds));
        log.info("刷新企业微信 AccessToken 成功 credentialId={} ttl={}s", credential.getId(), ttlSeconds);
        return response.accessToken();
    }

    private Credential loadEnabled(Long credentialId) {
        Credential credential = credentialService.getById(credentialId);
        if (credential == null || !Integer.valueOf(1).equals(credential.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "凭证不存在或已停用");
        }
        return credential;
    }

    private Credential findWorkflowCredential(Long workflowId) {
        return credentialService.getOne(new LambdaQueryWrapper<Credential>()
                .eq(Credential::getCredentialType, TYPE_WECOM)
                .eq(Credential::getScope, SCOPE_WORKFLOW)
                .eq(Credential::getWorkflowId, workflowId)
                .eq(Credential::getStatus, 1)
                .last("LIMIT 1"));
    }

    private Credential findGlobalCredential() {
        return credentialService.getOne(new LambdaQueryWrapper<Credential>()
                .eq(Credential::getCredentialType, TYPE_WECOM)
                .eq(Credential::getScope, SCOPE_GLOBAL)
                .eq(Credential::getStatus, 1)
                .orderByAsc(Credential::getId)
                .last("LIMIT 1"));
    }

    private void validateWecom(Credential credential) {
        if (!TYPE_WECOM.equalsIgnoreCase(credential.getCredentialType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "凭证类型不是企业微信");
        }
    }

    private String cacheKey(Credential credential) {
        if (StringUtils.hasText(credential.getTokenCacheKey())) {
            return credential.getTokenCacheKey();
        }
        return RedisKeys.wecomToken(credential.getId());
    }
}
