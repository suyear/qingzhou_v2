package com.qingzhou.modules.execution.engine.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.constant.RedisKeys;
import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.credential.entity.Credential;
import com.qingzhou.modules.credential.service.CredentialService;
import com.qingzhou.modules.credential.support.HttpAuthCredentialSupport;
import com.qingzhou.modules.execution.engine.NodeHttpInvoker;
import com.qingzhou.modules.execution.engine.OutboundUrlGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2ClientCredentialsClient {

    private final CredentialService credentialService;
    private final AesEncryptor aesEncryptor;
    private final StringRedisTemplate redisOps;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Value("${qingzhou.http.allow-private-network:false}")
    private boolean allowPrivateNetwork;

    public String getAccessToken(Long credentialId) {
        Credential credential = credentialService.getById(credentialId);
        if (credential == null) {
            throw new BizException(ResultCode.NOT_FOUND, "OAuth2 凭证不存在");
        }
        if (credential.getStatus() != null && credential.getStatus() == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "OAuth2 凭证已停用");
        }
        String cached = redisOps.opsForValue().get(RedisKeys.oauth2Token(credentialId));
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        return refresh(credential);
    }

    public void evict(Long credentialId) {
        if (credentialId != null) {
            redisOps.delete(RedisKeys.oauth2Token(credentialId));
        }
    }

    private String refresh(Credential credential) {
        Map<String, Object> extra = HttpAuthCredentialSupport.readExtra(credential);
        Map<String, String> secrets = HttpAuthCredentialSupport.decryptSecrets(credential, aesEncryptor);
        String tokenUrl = HttpAuthCredentialSupport.stringValue(extra.get("tokenUrl"), null);
        String clientId = HttpAuthCredentialSupport.stringValue(extra.get("clientId"), null);
        String clientSecret = HttpAuthCredentialSupport.secretOr(secrets, "clientSecret", "secret");
        String scope = HttpAuthCredentialSupport.stringValue(extra.get("scope"), null);
        if (!StringUtils.hasText(tokenUrl) || !StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new BizException(ResultCode.BAD_REQUEST, "OAuth2 凭证缺少 tokenUrl / clientId / clientSecret");
        }

        try {
            URI tokenUri = URI.create(tokenUrl);
            OutboundUrlGuard.validate(tokenUri, allowPrivateNetwork);
            String body = "grant_type=client_credentials"
                    + (StringUtils.hasText(scope) ? "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8) : "");
            String basic = Base64.getEncoder().encodeToString(
                    (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(tokenUri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream ignored = response.body()) {
                    // 丢弃错误响应，避免连接悬挂
                }
                throw new BizException(ResultCode.THIRD_PARTY_ERROR,
                        "OAuth2 换票失败 HTTP " + response.statusCode());
            }
            NodeHttpInvoker.ReadBody read = NodeHttpInvoker.readLimited(response.body(), 65_536);
            if (read.truncated()) {
                throw new BizException(ResultCode.THIRD_PARTY_ERROR, "OAuth2 响应体过大");
            }
            String responseBody = read.text();
            Map<String, Object> json = objectMapper.readValue(responseBody, new TypeReference<>() {
            });
            Object token = json.get("access_token");
            if (token == null || !StringUtils.hasText(String.valueOf(token))) {
                throw new BizException(ResultCode.THIRD_PARTY_ERROR, "OAuth2 响应缺少 access_token");
            }
            String accessToken = String.valueOf(token);
            long expiresIn = 3600;
            Object exp = json.get("expires_in");
            if (exp instanceof Number number) {
                expiresIn = Math.max(60, number.longValue());
            }
            long ttl = Math.max(30, expiresIn - 60);
            if (credential.getId() != null) {
                redisOps.opsForValue().set(RedisKeys.oauth2Token(credential.getId()), accessToken, ttl, TimeUnit.SECONDS);
                log.info("刷新 OAuth2 AccessToken 成功 credentialId={} ttl={}s", credential.getId(), ttl);
            }
            return accessToken;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ResultCode.THIRD_PARTY_ERROR, "OAuth2 换票失败");
        }
    }

    /** 未入库试连通：直接换票，不写缓存 */
    public String probeAccessToken(Credential credential) {
        return refresh(credential);
    }
}
