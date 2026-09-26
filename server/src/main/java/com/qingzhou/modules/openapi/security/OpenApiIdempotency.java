package com.qingzhou.modules.openapi.security;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 开放 API 可选幂等键。未带头时行为不变。
 * 同一应用、同一路径、同一键必须对应同一请求体，否则返回 409。
 */
public final class OpenApiIdempotency {

    public static final String HEADER = "X-Idempotency-Key";
    public static final String REPLAY_HEADER = "X-Idempotent-Replay";
    public static final int MAX_STORED_BODY = 512 * 1024;

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:\\-]{7,127}$");
    private static final HexFormat HEX = HexFormat.of();

    private OpenApiIdempotency() {
    }

    /**
     * @return null 表示调用方未提供幂等键
     */
    public static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim();
        if (!KEY.matcher(key).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "X-Idempotency-Key 需为 8–128 位字母、数字或 . _ : -");
        }
        return key;
    }

    public static String fingerprint(String method, String uri, String idemKey) {
        String material = (method == null ? "" : method.toUpperCase()) + "\n"
                + (uri == null ? "" : uri) + "\n"
                + idemKey;
        return sha256(material);
    }

    public static String bodyHash(String body) {
        return sha256(body == null ? "" : body);
    }

    public static String pack(int status, String bodyHash, String responseBody) {
        return status + "\n" + bodyHash + "\n" + (responseBody == null ? "" : responseBody);
    }

    public static Packed unpack(String stored) {
        if (stored == null) {
            return null;
        }
        int first = stored.indexOf('\n');
        if (first <= 0) {
            return null;
        }
        int second = stored.indexOf('\n', first + 1);
        if (second <= first) {
            return null;
        }
        int status;
        try {
            status = Integer.parseInt(stored.substring(0, first));
        } catch (NumberFormatException ex) {
            return null;
        }
        String hash = stored.substring(first + 1, second);
        String body = stored.substring(second + 1);
        return new Packed(status, hash, body);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    public record Packed(int status, String bodyHash, String body) {
    }
}
