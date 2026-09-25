package com.qingzhou.modules.execution.engine.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacSignerTest {

    @Test
    void signsWithAccessKeyAndTimestamp() {
        Map<String, String> headers = HmacSigner.signHeaders(
                "ak-demo",
                "sk-demo",
                "GET",
                "/v1/data",
                "HmacSHA256",
                "X-Signature",
                "{method}\\n{path}\\n{timestamp}\\n{nonce}\\n{accessKey}",
                true,
                true,
                "X-Timestamp",
                "X-Nonce");
        assertEquals("ak-demo", headers.get("X-Access-Key"));
        assertTrue(headers.containsKey("X-Signature"));
        assertTrue(headers.containsKey("X-Timestamp"));
        assertTrue(headers.containsKey("X-Nonce"));
        assertEquals(64, headers.get("X-Signature").length());
    }
}
