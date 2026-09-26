package com.qingzhou.modules.openapi.security;

import com.qingzhou.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiIdempotencyTest {

    @Test
    void absentKeyIsIgnored() {
        assertNull(OpenApiIdempotency.normalizeKey(null));
        assertNull(OpenApiIdempotency.normalizeKey("   "));
    }

    @Test
    void rejectsShortOrWeirdKey() {
        assertThrows(BizException.class, () -> OpenApiIdempotency.normalizeKey("short"));
        assertThrows(BizException.class, () -> OpenApiIdempotency.normalizeKey("bad key!!"));
    }

    @Test
    void packRoundTripKeepsBody() {
        String hash = OpenApiIdempotency.bodyHash("{\"userId\":1}");
        String packed = OpenApiIdempotency.pack(200, hash, "{\"code\":0,\"data\":{\"output\":{\"id\":1}}}");
        OpenApiIdempotency.Packed parsed = OpenApiIdempotency.unpack(packed);
        assertEquals(200, parsed.status());
        assertEquals(hash, parsed.bodyHash());
        assertEquals("{\"code\":0,\"data\":{\"output\":{\"id\":1}}}", parsed.body());
    }

    @Test
    void fingerprintChangesWithPath() {
        String a = OpenApiIdempotency.fingerprint("POST", "/openapi/v1/workflows/a/execute", "idem-key-1");
        String b = OpenApiIdempotency.fingerprint("POST", "/openapi/v1/workflows/b/execute", "idem-key-1");
        assertNotEquals(a, b);
    }
}
