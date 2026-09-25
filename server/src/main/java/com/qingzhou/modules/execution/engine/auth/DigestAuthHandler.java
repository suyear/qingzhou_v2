package com.qingzhou.modules.execution.engine.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP Digest Auth：根据 WWW-Authenticate 挑战计算 Authorization。
 */
public final class DigestAuthHandler {

    private DigestAuthHandler() {
    }

    public static boolean isDigestChallenge(String wwwAuthenticate) {
        return wwwAuthenticate != null && wwwAuthenticate.toLowerCase(Locale.ROOT).startsWith("digest ");
    }

    public static String buildAuthorization(
            String wwwAuthenticate,
            String username,
            String password,
            String method,
            String uriPath) {
        Map<String, String> challenge = parseChallenge(wwwAuthenticate);
        String realm = challenge.getOrDefault("realm", "");
        String nonce = challenge.getOrDefault("nonce", "");
        String opaque = challenge.get("opaque");
        String qop = challenge.get("qop");
        String algorithm = challenge.getOrDefault("algorithm", "MD5");
        String nc = "00000001";
        String cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        String ha1 = md5(username + ":" + realm + ":" + password);
        String ha2 = md5(method.toUpperCase(Locale.ROOT) + ":" + uriPath);
        String response;
        boolean useQop = qop != null && (qop.contains("auth") || "auth".equalsIgnoreCase(qop));
        if (useQop) {
            response = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2);
        } else {
            response = md5(ha1 + ":" + nonce + ":" + ha2);
        }

        StringBuilder auth = new StringBuilder();
        auth.append("Digest username=\"").append(escape(username)).append("\"")
                .append(", realm=\"").append(escape(realm)).append("\"")
                .append(", nonce=\"").append(escape(nonce)).append("\"")
                .append(", uri=\"").append(escape(uriPath)).append("\"")
                .append(", algorithm=").append(algorithm)
                .append(", response=\"").append(response).append("\"");
        if (useQop) {
            auth.append(", qop=auth, nc=").append(nc)
                    .append(", cnonce=\"").append(cnonce).append("\"");
        }
        if (opaque != null) {
            auth.append(", opaque=\"").append(escape(opaque)).append("\"");
        }
        return auth.toString();
    }

    public static Map<String, String> parseChallenge(String header) {
        Map<String, String> map = new LinkedHashMap<>();
        if (header == null) {
            return map;
        }
        String body = header.trim();
        if (body.toLowerCase(Locale.ROOT).startsWith("digest ")) {
            body = body.substring(7);
        }
        for (String part : body.split(",")) {
            String item = part.trim();
            int eq = item.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = item.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = item.substring(eq + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(key, value);
        }
        return map;
    }

    private static String md5(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("MD5 失败", ex);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
