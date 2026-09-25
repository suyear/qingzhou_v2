package com.qingzhou.modules.execution.engine.sql;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SqlParamBinder {

    private SqlParamBinder() {
    }

    public static void bind(PreparedStatement statement, BoundSql bound, Map<String, Object> payload) throws SQLException {
        Map<String, Object> params = payload == null ? Map.of() : payload;
        for (int i = 0; i < bound.paramNames().size(); i++) {
            String name = bound.paramNames().get(i);
            if (!params.containsKey(name)) {
                throw new BizException(ResultCode.BAD_REQUEST, "缺少 SQL 参数: " + name);
            }
            Object value = params.get(name);
            if (value instanceof Collection<?> || (value != null && value.getClass().isArray())) {
                throw new BizException(ResultCode.BAD_REQUEST, "参数 " + name + " 暂不支持数组，请传入单个值");
            }
            if (value instanceof Map<?, ?>) {
                throw new BizException(ResultCode.BAD_REQUEST, "参数 " + name + " 不能是对象");
            }
            statement.setObject(i + 1, normalize(value));
        }
    }

    public static Map<String, Object> summary(BoundSql bound, Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> params = payload == null ? Map.of() : payload;
        for (String name : bound.paramNames()) {
            Object value = params.get(name);
            out.put(name, mask(name, value));
        }
        return out;
    }

    static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = String.valueOf(value);
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }
        return value;
    }

    static Object mask(String name, Object value) {
        if (value == null) {
            return null;
        }
        String key = name == null ? "" : name.toLowerCase();
        if (key.contains("password") || key.contains("secret") || key.contains("token") || key.contains("cipher")) {
            return "***";
        }
        String text = String.valueOf(value);
        if (text.length() > 120) {
            return text.substring(0, 117) + "...";
        }
        return value;
    }
}
