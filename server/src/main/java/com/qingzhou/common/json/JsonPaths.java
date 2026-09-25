package com.qingzhou.common.json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 极简 JSON Path：支持 $.chatid / chatid / $.a.b
 */
public final class JsonPaths {

    private JsonPaths() {
    }

    public static Object get(Object source, String path) {
        if (source == null || path == null || path.isBlank()) {
            return null;
        }
        Object cursor = source;
        for (String key : split(path)) {
            if (!(cursor instanceof Map<?, ?> map)) {
                return null;
            }
            cursor = map.get(key);
        }
        return cursor;
    }

    @SuppressWarnings("unchecked")
    public static void put(Map<String, Object> target, String path, Object value) {
        if (target == null || path == null || path.isBlank()) {
            return;
        }
        String[] keys = split(path);
        Map<String, Object> cursor = target;
        for (int i = 0; i < keys.length - 1; i++) {
            Object next = cursor.get(keys[i]);
            if (next instanceof Map<?, ?> map) {
                cursor = (Map<String, Object>) map;
            } else {
                Map<String, Object> created = new LinkedHashMap<>();
                cursor.put(keys[i], created);
                cursor = created;
            }
        }
        cursor.put(keys[keys.length - 1], value);
    }

    private static String[] split(String path) {
        String normalized = path.trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized.split("\\.");
    }
}
