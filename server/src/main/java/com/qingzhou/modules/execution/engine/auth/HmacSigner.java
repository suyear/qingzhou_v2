package com.qingzhou.modules.execution.engine.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AK/SK HMAC 签名。默认签名串模板：
 * {method}\n{path}\n{timestamp}\n{nonce}\n{accessKey}
 */
public final class HmacSigner {

    private HmacSigner() {
    }

    public static Map<String, String> signHeaders(
            String accessKey,
            String secretKey,
            String method,
            String path,
            String alg,
            String signHeader,
            String signTemplate,
            boolean includeTimestamp,
            boolean includeNonce,
            String timestampHeader,
            String nonceHeader) {

        Map<String, String> headers = new LinkedHashMap<>();
        if (accessKey == null || secretKey == null) {
            return headers;
        }
        String algorithm = (alg == null || alg.isBlank()) ? "HmacSHA256" : alg.trim();
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String safeMethod = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        String safePath = path == null || path.isBlank() ? "/" : path;

        String template = (signTemplate == null || signTemplate.isBlank())
                ? "{method}\\n{path}\\n{timestamp}\\n{nonce}\\n{accessKey}"
                : signTemplate;
        // UI/JSON 常写成字面量 \n
        template = template.replace("\\n", "\n");
        String payload = template
                .replace("{method}", safeMethod)
                .replace("{path}", safePath)
                .replace("{timestamp}", ts)
                .replace("{nonce}", nonce)
                .replace("{accessKey}", accessKey);

        String signature = hmac(algorithm, secretKey, payload);
        headers.put(blankTo(signHeader, "X-Signature"), signature);
        headers.put("X-Access-Key", accessKey);
        if (includeTimestamp) {
            headers.put(blankTo(timestampHeader, "X-Timestamp"), ts);
        }
        if (includeNonce) {
            headers.put(blankTo(nonceHeader, "X-Nonce"), nonce);
        }
        return headers;
    }

    public static String hmac(String algorithm, String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC 签名失败: " + ex.getMessage(), ex);
        }
    }

    public static String randomNonce() {
        byte[] buf = new byte[16];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
