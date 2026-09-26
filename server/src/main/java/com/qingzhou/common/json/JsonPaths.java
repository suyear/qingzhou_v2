package com.qingzhou.common.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON Path：支持 $.chatid / chatid / $.a.b / $.rows[0].id / $.rows.0.id
 */
public final class JsonPaths {

    private JsonPaths() {
    }

    public static Object get(Object source, String path) {
        if (source == null) {
            return null;
        }
        // 空路径 / "$" 表示取整对象（步骤完整响应）
        if (path == null || path.isBlank() || "$".equals(path.trim())) {
            return source;
        }
        Object cursor = source;
        for (String key : tokens(path)) {
            if (key.isEmpty()) {
                continue;
            }
            if (cursor instanceof Map<?, ?> map) {
                cursor = map.get(key);
                continue;
            }
            if (cursor instanceof List<?> list && isIndex(key)) {
                int idx = Integer.parseInt(key);
                cursor = idx >= 0 && idx < list.size() ? list.get(idx) : null;
                continue;
            }
            return null;
        }
        return cursor;
    }

    @SuppressWarnings("unchecked")
    public static void put(Map<String, Object> target, String path, Object value) {
        if (target == null || path == null || path.isBlank()) {
            return;
        }
        List<String> keys = tokens(path);
        if (keys.isEmpty()) {
            return;
        }
        Map<String, Object> cursor = target;
        for (int i = 0; i < keys.size() - 1; i++) {
            String key = keys.get(i);
            Object next = cursor.get(key);
            if (next instanceof Map<?, ?> map) {
                cursor = (Map<String, Object>) map;
            } else {
                Map<String, Object> created = new LinkedHashMap<>();
                cursor.put(key, created);
                cursor = created;
            }
        }
        cursor.put(keys.get(keys.size() - 1), value);
    }

    private static boolean isIndex(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 将 $.a.b[0].c / a.0.c 拆成 [a,b,0,c] */
    static List<String> tokens(String path) {
        String normalized = path.trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        // rows[0].id -> rows.0.id
        StringBuilder buf = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '[') {
                int end = normalized.indexOf(']', i);
                if (end > i + 1) {
                    String inside = normalized.substring(i + 1, end).trim();
                    if (isIndex(inside)) {
                        if (buf.length() > 0 && buf.charAt(buf.length() - 1) != '.') {
                            buf.append('.');
                        }
                        buf.append(inside);
                        i = end;
                        continue;
                    }
                }
            }
            buf.append(c);
        }
        List<String> out = new ArrayList<>();
        for (String part : buf.toString().split("\\.")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }
}
