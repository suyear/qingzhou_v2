package com.qingzhou.modules.execution.engine.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtCredentialSignerTest {

    @Test
    void staticModeReturnsToken() {
        String token = JwtCredentialSigner.resolveBearerToken(
                "static", "abc.def.ghi", "HS256", null, null, null, null, null, null);
        assertEquals("abc.def.ghi", token);
    }

    @Test
    void signsHs256Jwt() {
        String jwt = JwtCredentialSigner.resolveBearerToken(
                "sign", null, "HS256", "super-secret-key", "qingzhou", "api", "user-1", 600, null);
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        assertTrue(parts[0].length() > 10);
        assertTrue(parts[2].length() > 10);
    }
}
