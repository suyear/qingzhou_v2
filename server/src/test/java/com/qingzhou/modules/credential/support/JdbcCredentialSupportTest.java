package com.qingzhou.modules.credential.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcCredentialSupportTest {

    @Test
    void mysqlUrlAndDriver() {
        var endpoint = JdbcCredentialSupport.validate("mysql", "127.0.0.1", 3306, "demo", "root");
        assertTrue(JdbcCredentialSupport.jdbcUrl(endpoint).startsWith("jdbc:mysql://127.0.0.1:3306/demo"));
        assertEquals("com.mysql.cj.jdbc.Driver", JdbcCredentialSupport.driverClassName("mysql"));
        assertEquals("SELECT 1", JdbcCredentialSupport.validationQuery("mysql"));
    }

    @Test
    void postgresDefaults() {
        assertEquals(5432, JdbcCredentialSupport.defaultPort("postgresql"));
        var endpoint = JdbcCredentialSupport.validate("pg", "db.local", null, "app", "u");
        assertEquals("postgresql", endpoint.dbType());
        assertEquals(5432, endpoint.port());
        assertEquals("jdbc:postgresql://db.local:5432/app", JdbcCredentialSupport.jdbcUrl(endpoint));
    }

    @Test
    void oracleValidationQuery() {
        assertEquals("SELECT 1 FROM DUAL", JdbcCredentialSupport.validationQuery("oracle"));
        assertEquals("oracle.jdbc.OracleDriver", JdbcCredentialSupport.driverClassName("oracle"));
    }

    @Test
    void legacyMysqlTypeRecognized() {
        assertTrue(JdbcCredentialSupport.isDatabase("MYSQL"));
        assertTrue(JdbcCredentialSupport.isDatabase("DATABASE"));
    }
}
