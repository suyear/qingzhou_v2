package com.qingzhou.common.crypto;

import com.qingzhou.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureUtilTest {

    private static final String SECRET = "test-app-secret";

    @Test
    void signAndVerifySuccess() {
        String body = "{\"name\":\"qingzhou\"}";
        String timestamp = SignatureUtil.currentTimestamp();
        String nonce = SignatureUtil.randomNonce();
        String signature = SignatureUtil.sign(body, timestamp, nonce, SECRET);
        assertDoesNotThrow(() -> SignatureUtil.verifyOrThrow(body, timestamp, nonce, SECRET, signature));
    }

    @Test
    void emptyBodyUsesMd5OfEmptyString() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", SignatureUtil.md5Hex(null));
        assertEquals(SignatureUtil.md5Hex(""), SignatureUtil.md5Hex(null));
    }

    @Test
    void differentSecretProducesDifferentSignature() {
        String ts = SignatureUtil.currentTimestamp();
        String nonce = "abc";
        assertNotEquals(
                SignatureUtil.sign("{}", ts, nonce, "secret-a"),
                SignatureUtil.sign("{}", ts, nonce, "secret-b"));
    }

    @Test
    void rejectStaleTimestamp() {
        String stale = String.valueOf(System.currentTimeMillis() - 6 * 60 * 1000L);
        BizException ex = assertThrows(BizException.class, () -> SignatureUtil.assertTimestampFresh(stale));
        assertEquals(401, ex.getCode());
        assertTrue(ex.getMessage().contains("5 分钟"));
    }

    @Test
    void rejectFutureTimestamp() {
        String future = String.valueOf(System.currentTimeMillis() + 6 * 60 * 1000L);
        assertThrows(BizException.class, () -> SignatureUtil.assertTimestampFresh(future));
    }

    @Test
    void acceptSecondLevelTimestamp() {
        String seconds = String.valueOf(System.currentTimeMillis() / 1000);
        assertDoesNotThrow(() -> SignatureUtil.assertTimestampFresh(seconds));
    }

    @Test
    void rejectTamperedBody() {
        String ts = SignatureUtil.currentTimestamp();
        String nonce = "n1";
        String signature = SignatureUtil.sign("{\"a\":1}", ts, nonce, SECRET);
        BizException ex = assertThrows(BizException.class,
                () -> SignatureUtil.verifyOrThrow("{\"a\":2}", ts, nonce, SECRET, signature));
        assertEquals(401, ex.getCode());
    }
}
