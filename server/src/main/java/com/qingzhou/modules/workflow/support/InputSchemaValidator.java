package com.qingzhou.modules.workflow.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 工作流入参 JSON Schema 子集：required + properties.type。
 * Schema 为空或没有 required/properties 时跳过。
 */
public final class InputSchemaValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InputSchemaValidator() {
    }

    public static void validate(String schemaJson, Map<String, Object> input) {
        Map<String, Object> schema = readMap(schemaJson);
        if (schema == null || schema.isEmpty()) {
            return;
        }
        Map<String, Object> payload = input == null ? Map.of() : input;
        List<String> required = asStringList(schema.get("required"));
        for (String key : required) {
            if (!payload.containsKey(key) || payload.get(key) == null || isBlankString(payload.get(key))) {
                throw new BizException(ResultCode.BAD_REQUEST, "缺少入参: " + key);
            }
        }
        Object propertiesRaw = schema.get("properties");
        if (!(propertiesRaw instanceof Map<?, ?> properties)) {
            return;
        }
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!payload.containsKey(key) || payload.get(key) == null) {
                continue;
            }
            String expected = readType(entry.getValue());
            if (expected != null && !typeMatches(expected, payload.get(key))) {
                throw new BizException(ResultCode.BAD_REQUEST, "入参 " + key + " 类型应为 " + expected);
            }
        }
    }

    private static Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readType(Object spec) {
        if (!(spec instanceof Map<?, ?> map)) {
            return null;
        }
        Object type = map.get("type");
        return type == null ? null : String.valueOf(type);
    }

    private static List<String> asStringList(Object raw) {
        if (!(raw instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().map(String::valueOf).filter(StringUtils::hasText).toList();
    }

    private static boolean isBlankString(Object value) {
        return value instanceof String text && text.isBlank();
    }

    private static boolean typeMatches(String expected, Object value) {
        return switch (expected) {
            case "string" -> true;
            case "integer" -> isInteger(value);
            case "number" -> isNumber(value);
            case "boolean" -> value instanceof Boolean || "true".equalsIgnoreCase(String.valueOf(value))
                    || "false".equalsIgnoreCase(String.valueOf(value));
            case "array" -> value instanceof Collection<?> || value.getClass().isArray();
            case "object" -> value instanceof Map<?, ?>;
            default -> true;
        };
    }

    private static boolean isInteger(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            return true;
        }
        if (value instanceof Double d) {
            return d == Math.rint(d);
        }
        if (value instanceof Float f) {
            return f == Math.rint(f);
        }
        if (value instanceof String text) {
            try {
                Long.parseLong(text.trim());
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean isNumber(Object value) {
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof String text) {
            try {
                Double.parseDouble(text.trim());
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }
}
