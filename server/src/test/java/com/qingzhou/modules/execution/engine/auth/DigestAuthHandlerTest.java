package com.qingzhou.modules.execution.engine.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DigestAuthHandlerTest {

    @Test
    void buildsDigestAuthorization() {
        String challenge = "Digest realm=\"api\", nonce=\"abc123\", qop=\"auth\", algorithm=MD5";
        String auth = DigestAuthHandler.buildAuthorization(challenge, "user", "pass", "GET", "/resource");
        assertTrue(auth.startsWith("Digest "));
        assertTrue(auth.contains("username=\"user\""));
        assertTrue(auth.contains("response="));
        assertTrue(auth.contains("qop=auth"));
    }
}
