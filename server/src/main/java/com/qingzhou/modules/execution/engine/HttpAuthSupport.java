package com.qingzhou.modules.execution.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.modules.component.entity.ApiComponent;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从组件 extraConfig 解析鉴权与静态请求头，供试连通与工作流执行使用。
 */
public final class HttpAuthSupport {

    public record AuthResult(
            Map<String, String> headers,
            Map<String, String> queryParams,
            boolean needsWecomToken
    ) {
        public AuthResult {
            headers = headers == null ? Map.of() : headers;
            queryParams = queryParams == null ? Map.of() : queryParams;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpAuthSupport() {
    }

    public static AuthResult resolve(ApiComponent component) {
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> queryParams = new LinkedHashMap<>();
        boolean needsWecom = HttpUrlSupport.needsAccessToken(component);

        Map<String, Object> config = readConfig(component == null ? null : component.getExtraConfig());
        if (Boolean.TRUE.equals(config.get("needAccessToken"))) {
            needsWecom = true;
        }

        applyStaticHeaders(config, headers);

        Map<String, Object> auth = asMap(config.get("auth"));
        String type = stringValue(auth.get("type"), "none");
        switch (type) {
            case "bearer" -> applyBearer(headers, stringValue(auth.get("bearerToken"), null));
            case "apiKey" -> applyApiKey(headers, queryParams, auth);
            case "basic" -> applyBasic(headers, stringValue(auth.get("basicUsername"), null),
                    stringValue(auth.get("basicPassword"), ""));
            case "wecom" -> needsWecom = true;
            case "credential" -> {
                // credential 由 OutboundAuthResolver 处理；此处仅保留静态头
            }
            default -> {
                // none
            }
        }

        return new AuthResult(headers, queryParams, needsWecom);
    }

    /** 供 OutboundAuthResolver 复用解析逻辑 */
    public static Map<String, Object> readConfigPublic(String extraConfig) {
        return readConfig(extraConfig);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMapPublic(Object raw) {
        return asMap(raw);
    }

    public static String stringValuePublic(Object raw, String fallback) {
        return stringValue(raw, fallback);
    }

    public static URI appendQueryParams(URI uri, Map<String, String> params) {
        if (uri == null || params == null || params.isEmpty()) {
            return uri;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri);
        params.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null) {
                builder.replaceQueryParam(key, value);
            }
        });
        return builder.encode().build(true).toUri();
    }

    public static Map<String, String> maskHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return headers;
        }
        Map<String, String> masked = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if ("authorization".equals(lower) || lower.contains("token") || lower.contains("secret")
                    || lower.contains("api-key") || lower.endsWith("-key")) {
                masked.put(key, "***");
            } else {
                masked.put(key, value);
            }
        });
        return masked;
    }

    private static void applyStaticHeaders(Map<String, Object> config, Map<String, String> headers) {
        Object raw = config.get("headers");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Object enabled = row.get("enabled");
            if (enabled instanceof Boolean flag && !flag) {
                continue;
            }
            String key = stringValue(row.get("key"), null);
            String value = stringValue(row.get("value"), null);
            if (StringUtils.hasText(key) && value != null) {
                headers.put(key, value);
            }
        }
    }

    private static void applyBearer(Map<String, String> headers, String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        headers.put("Authorization", "Bearer " + token.trim());
    }

    private static void applyApiKey(Map<String, String> headers, Map<String, String> queryParams, Map<String, Object> auth) {
        String name = stringValue(auth.get("apiKeyName"), null);
        String value = stringValue(auth.get("apiKeyValue"), null);
        if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
            return;
        }
        String placement = stringValue(auth.get("apiKeyIn"), "header");
        if ("query".equalsIgnoreCase(placement)) {
            queryParams.put(name.trim(), value.trim());
        } else {
            headers.put(name.trim(), value.trim());
        }
    }

    private static void applyBasic(Map<String, String> headers, String username, String password) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        String raw = username.trim() + ":" + (password == null ? "" : password);
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        headers.put("Authorization", "Basic " + encoded);
    }

    private static Map<String, Object> readConfig(String extraConfig) {
        if (!StringUtils.hasText(extraConfig)) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(extraConfig, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String stringValue(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? fallback : text;
    }
}
