package com.qingzhou.modules.execution.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 清洗 DB / 节点结果中的冗余字段（如与 rows 重复的 columns）。
 * 历史日志里的 preview 统一升格为 rows，避免出参/学习踩空。
 */
public final class DbResultCleaner {

    private DbResultCleaner() {
    }

    @SuppressWarnings("unchecked")
    public static Object stripRedundant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if ("columns".equals(key)) {
                    continue;
                }
                cleaned.put(key, stripRedundant(entry.getValue()));
            }
            // preview → rows（仅当尚无 rows）
            if (cleaned.containsKey("preview") && !cleaned.containsKey("rows")) {
                cleaned.put("rows", cleaned.get("preview"));
            }
            cleaned.remove("preview");
            return cleaned;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>(collection.size());
            for (Object item : collection) {
                list.add(stripRedundant(item));
            }
            return list;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> stripMap(Map<String, Object> value) {
        Object cleaned = stripRedundant(value);
        if (cleaned instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
