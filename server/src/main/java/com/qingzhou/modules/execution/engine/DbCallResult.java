package com.qingzhou.modules.execution.engine;

import java.util.List;
import java.util.Map;

public record DbCallResult(
        boolean success,
        boolean timeout,
        String error,
        String displayUrl,
        String kind,
        int rowCount,
        int affectedRows,
        List<String> columns,
        Map<String, Object> logBody,
        Map<String, Object> output
) {

    public static DbCallResult okQuery(
            String displayUrl,
            int rowCount,
            List<String> columns,
            Map<String, Object> logBody,
            Map<String, Object> output,
            boolean truncated) {
        return new DbCallResult(true, false, truncated ? "查询成功，结果已截断" : "查询成功",
                displayUrl, "QUERY", rowCount, 0, columns, logBody, output);
    }

    public static DbCallResult okUpdate(String displayUrl, int affectedRows, Map<String, Object> output) {
        return new DbCallResult(true, false, "更新成功，影响 " + affectedRows + " 行",
                displayUrl, "UPDATE", 0, affectedRows, List.of(), output, output);
    }

    public static DbCallResult fail(String displayUrl, boolean timeout, String error) {
        return new DbCallResult(false, timeout, error, displayUrl, null, 0, 0, List.of(), Map.of(), Map.of());
    }
}
