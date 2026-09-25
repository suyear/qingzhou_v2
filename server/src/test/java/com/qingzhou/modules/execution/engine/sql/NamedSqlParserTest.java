package com.qingzhou.modules.execution.engine.sql;

import com.qingzhou.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedSqlParserTest {

    @Test
    void bindNamedParamsAndSkipLiterals() {
        BoundSql bound = NamedSqlParser.parse("""
                SELECT id, name FROM users
                WHERE id = :userId AND name = ':notParam' -- :commented
                AND nick = :nick
                """);
        assertEquals(SqlKind.QUERY, bound.kind());
        assertEquals(List.of("userId", "nick"), bound.paramNames());
        assertTrue(bound.jdbcSql().contains("id = ?"));
        assertTrue(bound.jdbcSql().contains("nick = ?"));
        assertTrue(bound.jdbcSql().contains("':notParam'"));
        assertTrue(bound.jdbcSql().contains("-- :commented"));
    }

    @Test
    void rejectMultipleStatements() {
        BizException ex = assertThrows(BizException.class,
                () -> NamedSqlParser.parse("SELECT 1; DROP TABLE users"));
        assertTrue(ex.getMessage().contains("多条"));
    }

    @Test
    void allowTrailingSemicolon() {
        BoundSql bound = NamedSqlParser.parse("SELECT id FROM t WHERE id = :id;");
        assertEquals(List.of("id"), bound.paramNames());
        assertEquals(SqlKind.QUERY, bound.kind());
    }

    @Test
    void detectUpdateKind() {
        BoundSql bound = NamedSqlParser.parse("UPDATE users SET name = :name WHERE id = :id");
        assertEquals(SqlKind.UPDATE, bound.kind());
        assertEquals(List.of("name", "id"), bound.paramNames());
    }
}

class SqlGuardTest {

    @Test
    void rejectDangerousAndReadWriteMismatch() {
        BizException outfile = assertThrows(BizException.class,
                () -> SqlGuard.validate("SELECT 1 INTO OUTFILE '/tmp/x'", SqlGuard.MODE_READ));
        assertTrue(outfile.getMessage().contains("不允许"));

        BizException writeOnRead = assertThrows(BizException.class,
                () -> SqlGuard.validate("DELETE FROM users WHERE id = :id", SqlGuard.MODE_READ));
        assertTrue(writeOnRead.getMessage().contains("只读"));

        BoundSql ok = SqlGuard.validate("SELECT * FROM users WHERE id = :userId", SqlGuard.MODE_READ);
        assertEquals("QUERY", SqlGuard.methodOf(ok));
        SqlGuard.validate("INSERT INTO t(name) VALUES (:name)", SqlGuard.MODE_WRITE);
    }

    @Test
    void rejectLoadFileAndDdl() {
        assertThrows(BizException.class, () -> SqlGuard.validate("SELECT LOAD_FILE('/etc/passwd')", "READ"));
        assertThrows(BizException.class, () -> SqlGuard.validate("DROP TABLE users", "WRITE"));
        assertThrows(BizException.class, () -> SqlGuard.validate("CALL do_something()", "WRITE"));
    }
}

class SqlParamBinderTest {

    @Test
    void maskSecretParams() {
        BoundSql bound = NamedSqlParser.parse("SELECT 1 FROM t WHERE token = :apiToken AND id = :id");
        Map<String, Object> summary = SqlParamBinder.summary(bound, Map.of("apiToken", "secret-value", "id", 7));
        assertEquals("***", summary.get("apiToken"));
        assertEquals(7, summary.get("id"));
    }
}
