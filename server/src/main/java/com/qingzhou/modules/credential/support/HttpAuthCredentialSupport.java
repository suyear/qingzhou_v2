package com.qingzhou.modules.credential.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.credential.entity.Credential;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * HTTP 出站鉴权凭证。兼容旧 credentialType=CUSTOM（视为 bearer，secret=Token）。
 */
public final class HttpAuthCredentialSupport {

    public static final String TYPE_HTTP_AUTH = "HTTP_AUTH";
    public static final String TYPE_CUSTOM_LEGACY = "CUSTOM";

    public static final String AUTH_BASIC = "basic";
    public static final String AUTH_BEARER = "bearer";
    public static final String AUTH_API_KEY = "apiKey";
    public static final String AUTH_DIGEST = "digest";
    public static final String AUTH_JWT = "jwt";
    public static final String AUTH_OAUTH2_CC = "oauth2_cc";
    public static final String AUTH_AKSK = "aksk";
    public static final String AUTH_COOKIE = "cookie";
    public static final String AUTH_MTLS = "mtls";

    private static final Set<String> AUTH_TYPES = Set.of(
            AUTH_BASIC, AUTH_BEARER, AUTH_API_KEY, AUTH_DIGEST, AUTH_JWT,
            AUTH_OAUTH2_CC, AUTH_AKSK, AUTH_COOKIE, AUTH_MTLS);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpAuthCredentialSupport() {
    }

    public static boolean isHttpAuth(Credential credential) {
        return credential != null && isHttpAuth(credential.getCredentialType());
    }

    public static boolean isHttpAuth(String type) {
        if (!StringUtils.hasText(type)) {
            return false;
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        return TYPE_HTTP_AUTH.equals(t) || TYPE_CUSTOM_LEGACY.equals(t);
    }

    public static String normalizeAuthType(String raw) {
        String type = StringUtils.hasText(raw) ? raw.trim() : AUTH_BEARER;
        if ("oauth2".equalsIgnoreCase(type) || "oauth2_client_credentials".equalsIgnoreCase(type)) {
            type = AUTH_OAUTH2_CC;
        }
        if ("api_key".equalsIgnoreCase(type)) {
            type = AUTH_API_KEY;
        }
        if (!AUTH_TYPES.contains(type)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "HTTP 鉴权类型仅支持 basic / bearer / apiKey / digest / jwt / oauth2_cc / aksk / cookie / mtls");
        }
        return type;
    }

    public static String resolveAuthType(Credential credential) {
        if (credential == null) {
            return AUTH_BEARER;
        }
        if (TYPE_CUSTOM_LEGACY.equalsIgnoreCase(credential.getCredentialType())) {
            return AUTH_BEARER;
        }
        Map<String, Object> extra = readMap(credential.getExtraConfig());
        return normalizeAuthType(stringValue(extra.get("authType"), AUTH_BEARER));
    }

    public static Map<String, Object> readExtra(Credential credential) {
        return readMap(credential == null ? null : credential.getExtraConfig());
    }

    /**
     * 解密密钥：多密钥类型返回 JSON map；单密钥返回 {"secret":"..."}。
     */
    public static Map<String, String> decryptSecrets(Credential credential, AesEncryptor aes) {
        Map<String, String> secrets = new LinkedHashMap<>();
        if (credential == null || !StringUtils.hasText(credential.getSecretCipher())) {
            return secrets;
        }
        String plain = aes.decrypt(credential.getSecretCipher());
        if (!StringUtils.hasText(plain)) {
            return secrets;
        }
        String trimmed = plain.trim();
        if (trimmed.startsWith("{")) {
            try {
                Map<String, Object> parsed = MAPPER.readValue(trimmed, new TypeReference<>() {
                });
                if (parsed != null) {
                    parsed.forEach((k, v) -> {
                        if (k != null && v != null) {
                            secrets.put(k, String.valueOf(v));
                        }
                    });
                    return secrets;
                }
            } catch (Exception ignored) {
                // fall through: treat as plain secret
            }
        }
        secrets.put("secret", plain);
        secrets.put("token", plain);
        secrets.put("password", plain);
        secrets.put("cookie", plain);
        return secrets;
    }

    public static String encryptSecrets(Map<String, String> secrets, AesEncryptor aes) {
        if (secrets == null || secrets.isEmpty()) {
            return null;
        }
        try {
            Map<String, String> cleaned = new LinkedHashMap<>();
            secrets.forEach((k, v) -> {
                if (StringUtils.hasText(k) && v != null) {
                    cleaned.put(k, v);
                }
            });
            if (cleaned.size() == 1 && cleaned.containsKey("secret")) {
                return aes.encrypt(cleaned.get("secret"));
            }
            return aes.encrypt(MAPPER.writeValueAsString(cleaned));
        } catch (Exception ex) {
            throw new BizException(ResultCode.BAD_REQUEST, "密钥加密失败");
        }
    }

    public static void validateConfig(String authType, Map<String, Object> extra, Map<String, String> secrets, boolean requireSecrets) {
        String type = normalizeAuthType(authType);
        switch (type) {
            case AUTH_BASIC, AUTH_DIGEST -> {
                requireText(extra, "username", "请填写用户名");
                if (requireSecrets) {
                    requireSecret(secrets, "password", "请填写密码");
                }
            }
            case AUTH_BEARER, AUTH_COOKIE -> {
                if (requireSecrets) {
                    requireSecret(secrets, type.equals(AUTH_COOKIE) ? "cookie" : "token",
                            type.equals(AUTH_COOKIE) ? "请填写 Cookie" : "请填写 Token");
                }
            }
            case AUTH_API_KEY -> {
                requireText(extra, "apiKeyName", "请填写 API Key 参数名");
                if (requireSecrets) {
                    requireSecret(secrets, "apiKeyValue", "请填写 API Key");
                }
            }
            case AUTH_JWT -> {
                String mode = stringValue(extra.get("jwtMode"), "static");
                if ("sign".equalsIgnoreCase(mode)) {
                    requireText(extra, "jwtAlg", "请填写 JWT 算法");
                    if (requireSecrets) {
                        requireSecret(secrets, "jwtSecret", "请填写 JWT 签名密钥");
                    }
                } else if (requireSecrets) {
                    requireSecret(secrets, "token", "请填写 JWT Token");
                }
            }
            case AUTH_OAUTH2_CC -> {
                requireText(extra, "tokenUrl", "请填写 Token URL");
                requireText(extra, "clientId", "请填写 Client ID");
                if (requireSecrets) {
                    requireSecret(secrets, "clientSecret", "请填写 Client Secret");
                }
            }
            case AUTH_AKSK -> {
                requireText(extra, "accessKey", "请填写 Access Key");
                if (requireSecrets) {
                    requireSecret(secrets, "secretKey", "请填写 Secret Key");
                }
            }
            case AUTH_MTLS -> {
                if (requireSecrets) {
                    requireSecret(secrets, "clientCert", "请填写客户端证书 PEM");
                    requireSecret(secrets, "privateKey", "请填写私钥 PEM");
                }
            }
            default -> {
            }
        }
    }

    public static boolean supportsConnectivityTest(String authType) {
        String type = normalizeAuthType(authType);
        return AUTH_OAUTH2_CC.equals(type) || AUTH_MTLS.equals(type)
                || AUTH_BASIC.equals(type) || AUTH_BEARER.equals(type)
                || AUTH_API_KEY.equals(type) || AUTH_JWT.equals(type)
                || AUTH_AKSK.equals(type) || AUTH_COOKIE.equals(type)
                || AUTH_DIGEST.equals(type);
    }

    private static void requireText(Map<String, Object> extra, String key, String message) {
        if (!StringUtils.hasText(stringValue(extra.get(key), null))) {
            throw new BizException(ResultCode.BAD_REQUEST, message);
        }
    }

    private static void requireSecret(Map<String, String> secrets, String key, String message) {
        if (secrets == null || !StringUtils.hasText(secrets.get(key))) {
            // bearer/cookie often use "secret" alias
            if (secrets != null && StringUtils.hasText(secrets.get("secret"))
                    && ("token".equals(key) || "cookie".equals(key) || "password".equals(key))) {
                return;
            }
            throw new BizException(ResultCode.BAD_REQUEST, message);
        }
    }

    public static Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    public static String stringValue(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? fallback : text;
    }

    public static String secretOr(Map<String, String> secrets, String... keys) {
        if (secrets == null) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.hasText(secrets.get(key))) {
                return secrets.get(key);
            }
        }
        return null;
    }
}
