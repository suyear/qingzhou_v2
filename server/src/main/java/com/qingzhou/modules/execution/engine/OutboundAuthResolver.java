package com.qingzhou.modules.execution.engine;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.component.entity.ApiComponent;
import com.qingzhou.modules.credential.entity.Credential;
import com.qingzhou.modules.credential.service.CredentialService;
import com.qingzhou.modules.credential.support.HttpAuthCredentialSupport;
import com.qingzhou.modules.execution.engine.auth.HmacSigner;
import com.qingzhou.modules.execution.engine.auth.JwtCredentialSigner;
import com.qingzhou.modules.execution.engine.auth.OAuth2ClientCredentialsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析组件鉴权：内联 + 引用凭证中心。
 */
@Component
@RequiredArgsConstructor
public class OutboundAuthResolver {

    public record ResolvedAuth(
            Map<String, String> headers,
            Map<String, String> queryParams,
            boolean needsWecomToken,
            boolean digestAuth,
            String digestUsername,
            String digestPassword,
            Long mtlsCredentialId
    ) {
        public ResolvedAuth {
            headers = headers == null ? Map.of() : headers;
            queryParams = queryParams == null ? Map.of() : queryParams;
        }

        public HttpAuthSupport.AuthResult toAuthResult() {
            return new HttpAuthSupport.AuthResult(headers, queryParams, needsWecomToken);
        }
    }

    private final CredentialService credentialService;
    private final AesEncryptor aesEncryptor;
    private final ObjectProvider<OAuth2ClientCredentialsClient> oauth2Client;

    public ResolvedAuth resolve(ApiComponent component, String httpMethod, URI uri) {
        HttpAuthSupport.AuthResult inline = HttpAuthSupport.resolve(component);
        Map<String, String> headers = new LinkedHashMap<>(inline.headers());
        Map<String, String> queryParams = new LinkedHashMap<>(inline.queryParams());
        boolean needsWecom = inline.needsWecomToken();
        boolean digestAuth = false;
        String digestUsername = null;
        String digestPassword = null;
        Long mtlsCredentialId = null;

        Map<String, Object> config = HttpAuthSupport.readConfigPublic(component == null ? null : component.getExtraConfig());
        Map<String, Object> auth = HttpAuthSupport.asMapPublic(config.get("auth"));
        String type = HttpAuthSupport.stringValuePublic(auth.get("type"), "none");

        if ("credential".equalsIgnoreCase(type)) {
            Long credentialId = longValue(auth.get("credentialId"));
            if (credentialId == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "请选择鉴权凭证");
            }
            Credential credential = credentialService.getById(credentialId);
            if (credential == null) {
                throw new BizException(ResultCode.NOT_FOUND, "鉴权凭证不存在");
            }
            if (credential.getStatus() != null && credential.getStatus() == 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "鉴权凭证已停用");
            }

            if ("WECOM".equalsIgnoreCase(credential.getCredentialType())) {
                needsWecom = true;
            } else if (HttpAuthCredentialSupport.isHttpAuth(credential)) {
                AuthMaterial material = applyHttpAuthCredential(credential, httpMethod, uri, headers, queryParams);
                digestAuth = material.digestAuth();
                digestUsername = material.digestUsername();
                digestPassword = material.digestPassword();
                mtlsCredentialId = material.mtlsCredentialId();
            } else {
                throw new BizException(ResultCode.BAD_REQUEST, "该凭证类型不能用于 HTTP 鉴权");
            }
        }

        return new ResolvedAuth(headers, queryParams, needsWecom, digestAuth, digestUsername, digestPassword, mtlsCredentialId);
    }

    private AuthMaterial applyHttpAuthCredential(
            Credential credential,
            String httpMethod,
            URI uri,
            Map<String, String> headers,
            Map<String, String> queryParams) {

        String authType = HttpAuthCredentialSupport.resolveAuthType(credential);
        Map<String, Object> extra = HttpAuthCredentialSupport.readExtra(credential);
        Map<String, String> secrets = HttpAuthCredentialSupport.decryptSecrets(credential, aesEncryptor);

        return switch (authType) {
            case HttpAuthCredentialSupport.AUTH_BASIC -> {
                String username = HttpAuthCredentialSupport.stringValue(extra.get("username"), null);
                String password = HttpAuthCredentialSupport.secretOr(secrets, "password", "secret");
                applyBasic(headers, username, password);
                yield AuthMaterial.none();
            }
            case HttpAuthCredentialSupport.AUTH_BEARER -> {
                String token = HttpAuthCredentialSupport.secretOr(secrets, "token", "secret");
                applyBearer(headers, token);
                yield AuthMaterial.none();
            }
            case HttpAuthCredentialSupport.AUTH_API_KEY -> {
                String name = HttpAuthCredentialSupport.stringValue(extra.get("apiKeyName"), "X-API-Key");
                String value = HttpAuthCredentialSupport.secretOr(secrets, "apiKeyValue", "secret");
                String placement = HttpAuthCredentialSupport.stringValue(extra.get("apiKeyIn"), "header");
                if ("query".equalsIgnoreCase(placement)) {
                    queryParams.put(name, value);
                } else {
                    headers.put(name, value);
                }
                yield AuthMaterial.none();
            }
            case HttpAuthCredentialSupport.AUTH_COOKIE -> {
                String cookie = HttpAuthCredentialSupport.secretOr(secrets, "cookie", "secret");
                if (StringUtils.hasText(cookie)) {
                    headers.put("Cookie", cookie);
                }
                yield AuthMaterial.none();
            }
            case HttpAuthCredentialSupport.AUTH_JWT -> {
                String mode = HttpAuthCredentialSupport.stringValue(extra.get("jwtMode"), "static");
                String token = JwtCredentialSigner.resolveBearerToken(
                        mode,
                        HttpAuthCredentialSupport.secretOr(secrets, "token", "secret"),
                        HttpAuthCredentialSupport.stringValue(extra.get("jwtAlg"), "HS256"),
                        HttpAuthCredentialSupport.secretOr(secrets, "jwtSecret", "secret", "privateKey"),
                        HttpAuthCredentialSupport.stringValue(extra.get("jwtIssuer"), null),
                        HttpAuthCredentialSupport.stringValue(extra.get("jwtAudience"), null),
                        HttpAuthCredentialSupport.stringValue(extra.get("jwtSubject"), null),
                        intValue(extra.get("jwtTtlSeconds"), 3600),
                        null);
                applyBearer(headers, token);
                yield AuthMaterial.none();
            }
            case HttpAuthCredentialSupport.AUTH_OAUTH2_CC -> {
                OAuth2ClientCredentialsClient client = oauth2Client.getObject();
                String token = client.getAccessToken(credential.getId());
                applyBearer(headers, token);
                yield AuthMaterial.none();
            }
            case HttpAuthCredentialSupport.AUTH_AKSK -> {
                String path = uri == null ? "/" : (uri.getRawPath() == null ? "/" : uri.getRawPath());
                if (uri != null && StringUtils.hasText(uri.getRawQuery())) {
                    path = path + "?" + uri.getRawQuery();
                }
                Map<String, String> signed = HmacSigner.signHeaders(
                        HttpAuthCredentialSupport.stringValue(extra.get("accessKey"), null),
                        HttpAuthCredentialSupport.secretOr(secrets, "secretKey", "secret"),
                        httpMethod,
                        path,
                        HttpAuthCredentialSupport.stringValue(extra.get("hmacAlg"), "HmacSHA256"),
                        HttpAuthCredentialSupport.stringValue(extra.get("signHeader"), "X-Signature"),
                        HttpAuthCredentialSupport.stringValue(extra.get("signTemplate"), null),
                        boolValue(extra.get("includeTimestamp"), true),
                        boolValue(extra.get("includeNonce"), true),
                        HttpAuthCredentialSupport.stringValue(extra.get("timestampHeader"), "X-Timestamp"),
                        HttpAuthCredentialSupport.stringValue(extra.get("nonceHeader"), "X-Nonce"));
                headers.putAll(signed);
                yield AuthMaterial.none();
            }
            case HttpAuthCredentialSupport.AUTH_DIGEST -> {
                String username = HttpAuthCredentialSupport.stringValue(extra.get("username"), null);
                String password = HttpAuthCredentialSupport.secretOr(secrets, "password", "secret");
                yield new AuthMaterial(true, username, password, null);
            }
            case HttpAuthCredentialSupport.AUTH_MTLS -> new AuthMaterial(false, null, null, credential.getId());
            default -> AuthMaterial.none();
        };
    }

    private static void applyBearer(Map<String, String> headers, String token) {
        if (StringUtils.hasText(token)) {
            headers.put("Authorization", "Bearer " + token.trim());
        }
    }

    private static void applyBasic(Map<String, String> headers, String username, String password) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        String raw = username.trim() + ":" + (password == null ? "" : password);
        headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private static Long longValue(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null || !StringUtils.hasText(String.valueOf(raw))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null || !StringUtils.hasText(String.valueOf(raw))) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean boolValue(Object raw, boolean fallback) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw == null) {
            return fallback;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private record AuthMaterial(boolean digestAuth, String digestUsername, String digestPassword, Long mtlsCredentialId) {
        static AuthMaterial none() {
            return new AuthMaterial(false, null, null, null);
        }
    }
}
