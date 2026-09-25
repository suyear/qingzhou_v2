package com.qingzhou.modules.execution.engine;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.common.json.Jsons;
import com.qingzhou.modules.component.entity.ApiComponent;
import com.qingzhou.modules.execution.engine.sql.BoundSql;
import com.qingzhou.modules.execution.engine.sql.SqlGuard;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DatabaseComponentSupport {

    public static final String PROVIDER = "DATABASE";
    public static final String CATEGORY = "DATABASE";
    public static final int DEFAULT_MAX_ROWS = 200;
    public static final int MAX_ROWS_CAP = 2000;
    public static final int PREVIEW_ROWS = 20;

    private DatabaseComponentSupport() {
    }

    public static boolean isDatabase(ApiComponent component) {
        if (component == null) {
            return false;
        }
        return isDatabase(component.getProvider(), component.getCategory(), component.getHttpMethod());
    }

    public static boolean isDatabase(String provider, String category, String httpMethod) {
        if (PROVIDER.equalsIgnoreCase(provider) || CATEGORY.equalsIgnoreCase(category)) {
            return true;
        }
        String method = httpMethod == null ? "" : httpMethod.trim().toUpperCase();
        return "QUERY".equals(method) || "UPDATE".equals(method);
    }

    public static DatabaseSpec spec(ApiComponent component, Jsons jsons) {
        Map<String, Object> extra = readMap(component == null ? null : component.getExtraConfig(), jsons);
        Long datasourceId = longValue(extra.get("datasourceId"));
        if (datasourceId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "数据库组件未绑定数据源");
        }
        String sql = component.getUrlTemplate();
        if (!StringUtils.hasText(sql) && extra.get("sql") != null) {
            sql = String.valueOf(extra.get("sql"));
        }
        String accessMode = SqlGuard.normalizeMode(stringValue(extra.get("accessMode")));
        int maxRows = intValue(extra.get("maxRows"), DEFAULT_MAX_ROWS);
        if (maxRows <= 0) {
            maxRows = DEFAULT_MAX_ROWS;
        }
        maxRows = Math.min(maxRows, MAX_ROWS_CAP);
        BoundSql bound = SqlGuard.validate(sql, accessMode);
        return new DatabaseSpec(datasourceId, sql.trim(), accessMode, maxRows, SqlGuard.methodOf(bound), bound);
    }

    public static Map<String, Object> extraConfig(Long datasourceId, String accessMode, Integer maxRows) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("kind", PROVIDER);
        extra.put("datasourceId", datasourceId);
        extra.put("accessMode", SqlGuard.normalizeMode(accessMode));
        extra.put("maxRows", maxRows == null || maxRows <= 0 ? DEFAULT_MAX_ROWS : Math.min(maxRows, MAX_ROWS_CAP));
        return extra;
    }

    public static Map<String, Object> defaultResponseSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("rowCount", Map.of("type", "integer", "description", "查询返回行数"));
        properties.put("affectedRows", Map.of("type", "integer", "description", "更新影响行数"));
        properties.put("columns", Map.of("type", "array", "description", "查询列名"));
        properties.put("rows", Map.of("type", "array", "description", "查询行集"));
        properties.put("truncated", Map.of("type", "boolean", "description", "行集是否被截断"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    public static Map<String, Object> paramsToSchema(BoundSql bound) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String name : bound.paramNames()) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("type", "string");
            field.put("description", "SQL 参数 :" + name);
            properties.put(name, field);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", bound.paramNames());
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readMap(String json, Jsons jsons) {
        if (!StringUtils.hasText(json) || jsons == null) {
            return Map.of();
        }
        Object parsed = jsons.toObject(json);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static Long longValue(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String stringValue(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    public record DatabaseSpec(
            Long datasourceId,
            String sql,
            String accessMode,
            int maxRows,
            String method,
            BoundSql bound
    ) {
    }
}
