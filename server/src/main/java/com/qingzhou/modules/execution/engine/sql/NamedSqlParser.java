package com.qingzhou.modules.execution.engine.sql;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 解析命名参数 SQL（:userId），转为 JDBC {@code ?} 占位，并识别语句类型。
 * 字符串字面量与注释中的 {@code :name} 不会被当成参数。
 */
public final class NamedSqlParser {

    private NamedSqlParser() {
    }

    public static BoundSql parse(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "SQL 不能为空");
        }
        ParseResult parsed = transform(sql);
        if (parsed.jdbcSql.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "SQL 不能为空");
        }
        if (parsed.multiStatement) {
            throw new BizException(ResultCode.BAD_REQUEST, "禁止一次执行多条 SQL，请拆成多个数据库组件");
        }
        SqlKind kind = detectKind(parsed.leadKeyword);
        return new BoundSql(sql.trim(), parsed.jdbcSql, List.copyOf(parsed.paramNames), kind);
    }

    static String leadKeyword(String sql) {
        return transform(sql).leadKeyword;
    }

    private static ParseResult transform(String sql) {
        StringBuilder jdbc = new StringBuilder(sql.length());
        List<String> params = new ArrayList<>();
        StringBuilder keyword = new StringBuilder();
        boolean keywordDone = false;
        boolean sawSql = false;
        boolean multi = false;
        boolean afterSemicolon = false;

        State state = State.NORMAL;
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            char next = i + 1 < n ? sql.charAt(i + 1) : 0;

            if (state == State.LINE_COMMENT) {
                jdbc.append(c);
                if (c == '\n') {
                    state = State.NORMAL;
                }
                i++;
                continue;
            }
            if (state == State.BLOCK_COMMENT) {
                jdbc.append(c);
                if (c == '*' && next == '/') {
                    jdbc.append(next);
                    i += 2;
                    state = State.NORMAL;
                    continue;
                }
                i++;
                continue;
            }
            if (state == State.SQ) {
                jdbc.append(c);
                if (c == '\'' && next == '\'') {
                    jdbc.append(next);
                    i += 2;
                    continue;
                }
                if (c == '\'') {
                    state = State.NORMAL;
                }
                i++;
                continue;
            }
            if (state == State.DQ) {
                jdbc.append(c);
                if (c == '"' && next == '"') {
                    jdbc.append(next);
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    state = State.NORMAL;
                }
                i++;
                continue;
            }
            if (state == State.BT) {
                jdbc.append(c);
                if (c == '`') {
                    state = State.NORMAL;
                }
                i++;
                continue;
            }

            if (c == '-' && next == '-') {
                jdbc.append(c).append(next);
                i += 2;
                state = State.LINE_COMMENT;
                continue;
            }
            if (c == '/' && next == '*') {
                jdbc.append(c).append(next);
                i += 2;
                state = State.BLOCK_COMMENT;
                continue;
            }
            if (c == '\'') {
                jdbc.append(c);
                state = State.SQ;
                i++;
                continue;
            }
            if (c == '"') {
                jdbc.append(c);
                state = State.DQ;
                i++;
                continue;
            }
            if (c == '`') {
                jdbc.append(c);
                state = State.BT;
                i++;
                continue;
            }
            if (c == '#') {
                jdbc.append(c);
                state = State.LINE_COMMENT;
                i++;
                continue;
            }

            if (c == ';') {
                int look = i + 1;
                while (look < n && Character.isWhitespace(sql.charAt(look))) {
                    look++;
                }
                if (look < n && !isCommentStart(sql, look)) {
                    multi = true;
                }
                afterSemicolon = true;
                i++;
                continue;
            }

            if (c == ':' && isIdentStart(next) && !isColonPrefix(sql, i)) {
                int start = i + 1;
                int end = start;
                while (end < n && isIdentPart(sql.charAt(end))) {
                    end++;
                }
                String name = sql.substring(start, end);
                params.add(name);
                jdbc.append('?');
                sawSql = true;
                afterSemicolon = false;
                i = end;
                continue;
            }

            if (Character.isWhitespace(c)) {
                if (!keyword.isEmpty()) {
                    keywordDone = true;
                }
            } else {
                if (afterSemicolon && sawSql) {
                    multi = true;
                }
                afterSemicolon = false;
                sawSql = true;
                if (!keywordDone) {
                    if (keyword.length() < 16 && (keyword.isEmpty() ? isIdentStart(c) : isIdentPart(c))) {
                        keyword.append(Character.toUpperCase(c));
                    } else if (!keyword.isEmpty()) {
                        keywordDone = true;
                    }
                }
            }
            jdbc.append(c);
            i++;
        }

        ParseResult result = new ParseResult();
        result.jdbcSql = jdbc.toString().trim();
        result.paramNames = params;
        result.leadKeyword = keyword.toString();
        result.multiStatement = multi;
        return result;
    }

    private static boolean isCommentStart(String sql, int index) {
        if (index >= sql.length()) {
            return false;
        }
        char c = sql.charAt(index);
        char next = index + 1 < sql.length() ? sql.charAt(index + 1) : 0;
        return c == '#' || (c == '-' && next == '-') || (c == '/' && next == '*');
    }

    private static boolean isColonPrefix(String sql, int colonIndex) {
        if (colonIndex > 0 && sql.charAt(colonIndex - 1) == ':') {
            return true;
        }
        return colonIndex + 1 < sql.length() && sql.charAt(colonIndex + 1) == '=';
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }

    private static SqlKind detectKind(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return SqlKind.UNKNOWN;
        }
        String key = keyword.toUpperCase(Locale.ROOT);
        return switch (key) {
            case "SELECT", "WITH", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "TABLE" -> SqlKind.QUERY;
            case "INSERT", "UPDATE", "DELETE", "REPLACE" -> SqlKind.UPDATE;
            default -> SqlKind.UNKNOWN;
        };
    }

    private enum State {
        NORMAL, SQ, DQ, BT, LINE_COMMENT, BLOCK_COMMENT
    }

    private static final class ParseResult {
        private String jdbcSql;
        private List<String> paramNames;
        private String leadKeyword;
        private boolean multiStatement;
    }
}
