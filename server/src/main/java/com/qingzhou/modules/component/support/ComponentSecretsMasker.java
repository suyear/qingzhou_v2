package com.qingzhou.modules.component.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.modules.auth.security.AuthContext;
import com.qingzhou.modules.auth.security.AuthUserPrincipal;
import com.qingzhou.modules.component.entity.ApiComponent;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组件敏感配置脱敏：VIEWER 不可见内联 Token/密码；写角色可编辑完整配置。
 */
public final class ComponentSecretsMasker {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> SECRET_KEYS = Set.of(
            "bearerToken", "apiKeyValue", "basicPassword", "password", "token", "secret", "clientSecret"
    );
    private static final String MASK = "***";

    private ComponentSecretsMasker() {
    }

    public static ApiComponent maskForCurrentUser(ApiComponent component) {
        if (component == null) {
            return null;
        }
        if (canReadSecrets()) {
            return component;
        }
        return mask(component);
    }

    public static boolean canReadSecrets() {
        return AuthContext.current()
                .map(AuthUserPrincipal::getRoles)
                .map(roles -> roles != null && (roles.contains("ADMIN") || roles.contains("DEVELOPER")))
                .orElse(false);
    }

    public static ApiComponent mask(ApiComponent component) {
        if (component == null || !StringUtils.hasText(component.getExtraConfig())) {
            return component;
        }
        try {
            Map<String, Object> config = MAPPER.readValue(component.getExtraConfig(), new TypeReference<>() {
            });
            Object authRaw = config.get("auth");
            if (authRaw instanceof Map<?, ?> authMap) {
                Map<String, Object> auth = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : authMap.entrySet()) {
                    String key = String.valueOf(e.getKey());
                    Object value = e.getValue();
                    if (SECRET_KEYS.contains(key) && value != null && StringUtils.hasText(String.valueOf(value))) {
                        auth.put(key, MASK);
                    } else {
                        auth.put(key, value);
                    }
                }
                config.put("auth", auth);
            }
            if (config.get("headers") instanceof List<?> headers) {
                List<Map<String, Object>> maskedHeaders = headers.stream().map(item -> {
                    if (!(item instanceof Map<?, ?> raw)) {
                        return Map.<String, Object>of();
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    raw.forEach((k, v) -> row.put(String.valueOf(k), v));
                    Object value = row.get("value");
                    if (value != null && StringUtils.hasText(String.valueOf(value))) {
                        row.put("value", MASK);
                    }
                    return row;
                }).toList();
                config.put("headers", maskedHeaders);
            }
            component.setExtraConfig(MAPPER.writeValueAsString(config));
        } catch (Exception ignored) {
            // keep original if parse fails
        }
        return component;
    }
}
