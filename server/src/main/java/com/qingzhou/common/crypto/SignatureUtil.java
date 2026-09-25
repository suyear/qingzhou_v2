package com.qingzhou.common.crypto;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * OpenAPI 签名（阿里云风格：API Key + HMAC-SHA256 + 防重放）。
 *
 * 签名串（无分隔符拼接）：
 *   stringToSign = MD5(body) + timestamp + nonce + secret
 *   signature    = Hex(HMAC-SHA256(key=secret, data=stringToSign)).toLowerCase()
 *
 * 请求头约定（Phase 5 网关过滤器读取同名头）：
 *   X-App-Key    调用方 API Key
 *   X-Timestamp  毫秒时间戳（10 位秒级也会自动换算）
 *   X-Nonce      调用方生成的随机串，Redis 去重在网关层做
 *   X-Signature  本工具算出的十六进制 HMAC
 *
 * GET / 空 body 时按空字符串做 MD5（d41d8cd98f00b204e9800998ecf8427e）。
 */
public final class SignatureUtil {

    public static final String HEADER_APP_KEY = "X-App-Key";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";

    /** 时间戳允许的偏移：±5 分钟 */
    public static final long TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000L;

    private static final String HMAC_ALG = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();
    private static final SecureRandom RANDOM = new SecureRandom();

    private SignatureUtil() {
    }

    /**
     * 计算签名。body 为原始请求体；null 视为空串。
     */
    public static String sign(String body, String timestamp, String nonce, String secret) {
        assertNotBlank(timestamp, "timestamp");
        assertNotBlank(nonce, "nonce");
        assertNotBlank(secret, "secret");
        String stringToSign = md5Hex(body) + timestamp + nonce + secret;
        return hmacSha256Hex(stringToSign, secret);
    }

    /**
     * 校验时间戳新鲜度 + 签名。通过返回 true；失败抛 BizException。
     */
    public static void verifyOrThrow(String body, String timestamp, String nonce, String secret, String signature) {
        assertTimestampFresh(timestamp);
        if (!hasText(signature)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少签名");
        }
        String expect = sign(body, timestamp, nonce, secret);
        if (!constantTimeEquals(expect, signature.trim())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "签名不匹配");
        }
    }

    /**
     * 只校验时间戳是否在 ±5 分钟内。
     * 接受 13 位毫秒或 10 位秒。
     */
    public static void assertTimestampFresh(String timestamp) {
        if (!hasText(timestamp) || !timestamp.chars().allMatch(Character::isDigit)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "时间戳格式错误");
        }
        long ts = Long.parseLong(timestamp);
        if (timestamp.length() <= 10) {
            ts *= 1000L;
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - ts) > TIMESTAMP_TOLERANCE_MS) {
            throw new BizException(ResultCode.UNAUTHORIZED, "时间戳超出 ±5 分钟窗口，疑似重放或时钟偏移");
        }
    }

    public static String md5Hex(String body) {
        String raw = body == null ? "" : body;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }

    public static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return HEX.formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    public static String randomNonce() {
        byte[] buf = new byte[16];
        RANDOM.nextBytes(buf);
        return HEX.formatHex(buf);
    }

    public static String currentTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] right = b.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private static void assertNotBlank(String value, String name) {
        if (!hasText(value)) {
            throw new BizException(ResultCode.UNAUTHORIZED, name + " 不能为空");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
