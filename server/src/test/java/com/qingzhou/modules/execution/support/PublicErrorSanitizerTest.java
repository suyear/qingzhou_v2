package com.qingzhou.modules.execution.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicErrorSanitizerTest {

    @Test
    void stripsNodeNameButKeepsSafeReason() {
        assertEquals("HTTP 502",
                PublicErrorSanitizer.forOpenApi("FAILED", "节点 按照ID查询用户 失败: HTTP 502"));
        assertEquals("执行超时",
                PublicErrorSanitizer.forOpenApi("FAILED", "节点 按照ID查询用户 失败: 第三方接口超时"));
    }

    @Test
    void timeoutStatusIsGeneric() {
        assertEquals("执行超时", PublicErrorSanitizer.forOpenApi("TIMEOUT", "工作流执行超时（60000ms）"));
    }

    @Test
    void hidesSqlAndDriverText() {
        assertEquals("执行失败", PublicErrorSanitizer.forOpenApi("FAILED",
                "节点 查用户 失败: You have an error in your SQL syntax near 'select *'"));
        assertEquals("执行失败", PublicErrorSanitizer.forOpenApi("FAILED",
                "java.sql.SQLException: Communications link failure"));
    }
}
