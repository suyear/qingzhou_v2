package com.qingzhou.modules.execution.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbResultCleanerTest {

    @Test
    void stripsColumnsRecursively() {
        Map<String, Object> raw = Map.of(
                "node_a", Map.of(
                        "rowCount", 1,
                        "columns", List.of("id", "name"),
                        "rows", List.of(Map.of("id", 1, "name", "x"))
                )
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> cleaned = (Map<String, Object>) DbResultCleaner.stripRedundant(raw);
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) cleaned.get("node_a");
        assertEquals(1, node.get("rowCount"));
        assertFalse(node.containsKey("columns"));
        assertTrue(node.containsKey("rows"));
    }

    @Test
    void promotesPreviewToRows() {
        Map<String, Object> raw = Map.of(
                "rowCount", 1,
                "preview", List.of(Map.of("id", 1)),
                "truncated", false
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> cleaned = (Map<String, Object>) DbResultCleaner.stripRedundant(raw);
        assertTrue(cleaned.containsKey("rows"));
        assertFalse(cleaned.containsKey("preview"));
        assertEquals(1, ((List<?>) cleaned.get("rows")).size());
    }
}
