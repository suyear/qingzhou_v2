package com.qingzhou.modules.execution.engine.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT：静态 Token，或 HS256/RS256 本地签发后作为 Bearer。
 */
public final class JwtCredentialSigner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtCredentialSigner() {
    }

    public static String resolveBearerToken(
            String mode,
            String staticToken,
            String alg,
            String secretOrPrivateKey,
            String issuer,
            String audience,
            String subject,
            Integer ttlSeconds,
            Map<String, Object> extraClaims) {
        if (!"sign".equalsIgnoreCase(mode)) {
            return staticToken;
        }
        String algorithm = (alg == null || alg.isBlank()) ? "HS256" : alg.trim().toUpperCase();
        long now = Instant.now().getEpochSecond();
        long exp = now + (ttlSeconds == null || ttlSeconds <= 0 ? 3600 : ttlSeconds);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", algorithm);
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        if (extraClaims != null) {
            payload.putAll(extraClaims);
        }
        if (issuer != null && !issuer.isBlank()) {
            payload.put("iss", issuer);
        }
        if (audience != null && !audience.isBlank()) {
            payload.put("aud", audience);
        }
        if (subject != null && !subject.isBlank()) {
            payload.put("sub", subject);
        }
        payload.put("iat", now);
        payload.put("exp", exp);

        try {
            String headerPart = base64Url(MAPPER.writeValueAsBytes(header));
            String payloadPart = base64Url(MAPPER.writeValueAsBytes(payload));
            String signingInput = headerPart + "." + payloadPart;
            String signature = sign(algorithm, secretOrPrivateKey, signingInput);
            return signingInput + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 签发失败: " + ex.getMessage(), ex);
        }
    }

    private static String sign(String alg, String key, String signingInput) throws Exception {
        byte[] data = signingInput.getBytes(StandardCharsets.UTF_8);
        if ("HS256".equals(alg)) {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(data));
        }
        if ("RS256".equals(alg)) {
            PrivateKey privateKey = readPkcs8PrivateKey(key);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data);
            return base64Url(signature.sign());
        }
        throw new IllegalArgumentException("不支持的 JWT 算法: " + alg);
    }

    private static PrivateKey readPkcs8PrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static String base64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    @SuppressWarnings("unused")
    public static Map<String, Object> parseClaimsUnsafe(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return Map.of();
            }
            byte[] json = Base64.getUrlDecoder().decode(parts[1]);
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
