package com.qingzhou.modules.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtTokenService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;

    @Value("${qingzhou.auth.jwt-secret}")
    private String jwtSecret;

    @Value("${qingzhou.auth.token-ttl-hours:24}")
    private int ttlHours;

    public String issue(AuthUserPrincipal principal) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(principal.getId()));
        payload.put("username", principal.getUsername());
        payload.put("displayName", principal.getDisplayName());
        payload.put("roles", principal.getRoles());
        payload.put("mcp", principal.isMustChangePassword() ? 1 : 0);
        payload.put("iat", now);
        payload.put("exp", now + ttlSeconds());

        String h = B64.encodeToString(writeBytes(header));
        String p = B64.encodeToString(writeBytes(payload));
        return h + "." + p + "." + sign(h + "." + p);
    }

    public AuthUserPrincipal parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        if (!constantTimeEquals(sign(parts[0] + "." + parts[1]), parts[2])) {
            return null;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(B64D.decode(parts[1]), new TypeReference<>() {
            });
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                return null;
            }
            Long id = Long.valueOf(String.valueOf(payload.get("sub")));
            String username = String.valueOf(payload.get("username"));
            String displayName = String.valueOf(payload.getOrDefault("displayName", username));
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) payload.getOrDefault("roles", List.of());
            boolean mcp = Integer.parseInt(String.valueOf(payload.getOrDefault("mcp", 0))) == 1;
            return new AuthUserPrincipal(id, username, displayName, "", true, mcp, roles);
        } catch (Exception ex) {
            return null;
        }
    }

    public long ttlSeconds() {
        return Math.max(1, ttlHours) * 3600L;
    }

    private byte[] writeBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 序列化失败", ex);
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 签名失败", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
