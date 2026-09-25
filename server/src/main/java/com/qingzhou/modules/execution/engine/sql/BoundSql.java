package com.qingzhou.modules.execution.engine.sql;

import java.util.List;

public record BoundSql(String originalSql, String jdbcSql, List<String> paramNames, SqlKind kind) {
}
