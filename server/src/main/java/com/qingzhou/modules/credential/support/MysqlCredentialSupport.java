package com.qingzhou.modules.credential.support;

import com.qingzhou.modules.credential.entity.Credential;

import java.util.Map;

/**
 * @deprecated 使用 {@link JdbcCredentialSupport}；保留兼容旧调用点。
 */
@Deprecated
public final class MysqlCredentialSupport {

    public static final String TYPE = JdbcCredentialSupport.TYPE_MYSQL_LEGACY;

    private MysqlCredentialSupport() {
    }

    public static boolean isMysql(Credential credential) {
        return JdbcCredentialSupport.isDatabase(credential);
    }

    public static boolean isMysql(String type) {
        return JdbcCredentialSupport.isDatabase(type);
    }

    public static MysqlEndpoint from(Credential credential) {
        JdbcCredentialSupport.JdbcEndpoint e = JdbcCredentialSupport.from(credential);
        return new MysqlEndpoint(e.host(), e.port(), e.dbName(), e.username());
    }

    public static MysqlEndpoint from(Map<String, Object> extra) {
        JdbcCredentialSupport.JdbcEndpoint e = JdbcCredentialSupport.from(extra);
        return new MysqlEndpoint(e.host(), e.port(), e.dbName(), e.username());
    }

    public static Map<String, Object> toExtra(String host, Integer port, String dbName, String username) {
        return JdbcCredentialSupport.toExtra(JdbcCredentialSupport.DB_MYSQL, host, port, dbName, username);
    }

    public static MysqlEndpoint validate(String host, Integer port, String dbName, String username) {
        JdbcCredentialSupport.JdbcEndpoint e = JdbcCredentialSupport.validate(
                JdbcCredentialSupport.DB_MYSQL, host, port, dbName, username);
        return new MysqlEndpoint(e.host(), e.port(), e.dbName(), e.username());
    }

    public static String jdbcUrl(MysqlEndpoint endpoint) {
        return JdbcCredentialSupport.jdbcUrl(new JdbcCredentialSupport.JdbcEndpoint(
                JdbcCredentialSupport.DB_MYSQL, endpoint.host(), endpoint.port(), endpoint.dbName(), endpoint.username()));
    }

    public static String displayUrl(MysqlEndpoint endpoint) {
        if (endpoint == null) {
            return "mysql://";
        }
        return JdbcCredentialSupport.displayUrl(new JdbcCredentialSupport.JdbcEndpoint(
                JdbcCredentialSupport.DB_MYSQL, endpoint.host(), endpoint.port(), endpoint.dbName(), endpoint.username()));
    }

    public static Map<String, Object> readMap(String json) {
        return JdbcCredentialSupport.readMap(json);
    }

    public record MysqlEndpoint(String host, int port, String dbName, String username) {
        public String display() {
            return displayUrl(this);
        }
    }
}
