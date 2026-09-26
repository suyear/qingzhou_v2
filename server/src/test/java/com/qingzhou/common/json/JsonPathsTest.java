package com.qingzhou.common.json;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void blankPathReturnsWholeSource() {
        Map<String, Object> source = Map.of("chatid", "c1", "errcode", 0);
        assertEquals(source, JsonPaths.get(source, ""));
        assertEquals(source, JsonPaths.get(source, "$"));
        assertEquals(source, JsonPaths.get(source, "  "));
    }

    @Test
    void readArrayIndex() {
        Map<String, Object> row = Map.of("username", "alice", "id", 1);
        Map<String, Object> source = Map.of("rows", List.of(row), "rowCount", 1);
        assertEquals("alice", JsonPaths.get(source, "$.rows[0].username"));
        assertEquals("alice", JsonPaths.get(source, "$.rows.0.username"));
        assertEquals(1, JsonPaths.get(source, "rows[0].id"));
        assertNull(JsonPaths.get(source, "$.rows[9].username"));
    }
}
