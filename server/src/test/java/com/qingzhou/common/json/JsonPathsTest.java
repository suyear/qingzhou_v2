package com.qingzhou.common.json;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonPathsTest {

    @Test
    void readAndWriteNestedPath() {
        Map<String, Object> source = Map.of("chatid", "c1", "user", Map.of("id", "u1"));
        assertEquals("c1", JsonPaths.get(source, "$.chatid"));
        assertEquals("u1", JsonPaths.get(source, "$.user.id"));

        Map<String, Object> target = new HashMap<>();
        JsonPaths.put(target, "$.chatid", "c2");
        assertEquals("c2", target.get("chatid"));
    }
}
