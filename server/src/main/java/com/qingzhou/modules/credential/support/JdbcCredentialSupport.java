package com.qingzhou.modules.credential.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.credential.entity.Credential;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 多数据库 JDBC 凭证解析。兼容旧 credentialType=MYSQL（视为 dbType=mysql）。
 */
public final class JdbcCredentialSupport {

    public static final String TYPE_DATABASE = "DATABASE";
    public static final String TYPE_MYSQL_LEGACY = "MYSQL";

    public static final String DB_MYSQL = "mysql";
    public static final String DB_MARIADB = "mariadb";
    public static final String DB_POSTGRESQL = "postgresql";
    public static final String DB_SQLSERVER = "sqlserver";
    public static final String DB_ORACLE = "oracle";

    private static final Set<String> SUPPORTED = Set.of(
            DB_MYSQL, DB_MARIADB, DB_POSTGRESQL, DB_SQLSERVER, DB_ORACLE);

    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9._:-]+$");
    private static final Pattern DB_NAME = Pattern.compile("^[A-Za-z0-9_$#.-]+$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JdbcCredentialSupport() {
    }

    public static boolean isDatabase(Credential credential) {
        return credential != null && isDatabase(credential.getCredentialType());
    }

    public static boolean isDatabase(String type) {
        if (!StringUtils.hasText(type)) {
            return false;
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        return TYPE_DATABASE.equals(t) || TYPE_MYSQL_LEGACY.equals(t);
    }

    public static JdbcEndpoint from(Credential credential) {
        Map<String, Object> extra = readMap(credential == null ? null : credential.getExtraConfig());
        if (credential != null && TYPE_MYSQL_LEGACY.equalsIgnoreCase(credential.getCredentialType())
                && !StringUtils.hasText(trim(extra.get("dbType")))) {
            extra = new LinkedHashMap<>(extra);
            extra.put("dbType", DB_MYSQL);
        }
        return from(extra);
    }

    public static JdbcEndpoint from(Map<String, Object> extra) {
        Map<String, Object> map = extra == null ? Map.of() : extra;
        String dbType = normalizeDbType(trim(map.get("dbType")));
        int defaultPort = defaultPort(dbType);
        String host = trim(map.get("dbHost"));
        Integer port = intValue(map.get("dbPort"), defaultPort);
        String dbName = trim(map.get("dbName"));
        String username = trim(map.get("dbUsername"));
        return new JdbcEndpoint(dbType, host, port, dbName, username);
    }

    public static Map<String, Object> toExtra(String dbType, String host, Integer port, String dbName, String username) {
        JdbcEndpoint endpoint = validate(dbType, host, port, dbName, username);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("dbType", endpoint.dbType());
        extra.put("dbHost", endpoint.host());
        extra.put("dbPort", endpoint.port());
        extra.put("dbName", endpoint.dbName());
        extra.put("dbUsername", endpoint.username());
        return extra;
    }

    public static JdbcEndpoint validate(String dbType, String host, Integer port, String dbName, String username) {
        String type = normalizeDbType(dbType);
        String safeHost = trim(host);
        String safeDb = trim(dbName);
        String safeUser = trim(username);
        int safePort = port == null ? defaultPort(type) : port;
        if (!StringUtils.hasText(safeHost)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写数据库主机");
        }
        if (!HOST.matcher(safeHost).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "数据库主机格式不正确");
        }
        if (safePort < 1 || safePort > 65535) {
            throw new BizException(ResultCode.BAD_REQUEST, "数据库端口不合法");
        }
        if (!StringUtils.hasText(safeDb)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写数据库名");
        }
        if (!DB_NAME.matcher(safeDb).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "数据库名格式不正确");
        }
        if (!StringUtils.hasText(safeUser)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写数据库用户名");
        }
        return new JdbcEndpoint(type, safeHost, safePort, safeDb, safeUser);
    }

    public static String normalizeDbType(String raw) {
        String type = StringUtils.hasText(raw) ? raw.trim().toLowerCase(Locale.ROOT) : DB_MYSQL;
        if ("postgres".equals(type) || "pg".equals(type)) {
            type = DB_POSTGRESQL;
        }
        if ("mssql".equals(type) || "sql_server".equals(type)) {
            type = DB_SQLSERVER;
        }
        if (!SUPPORTED.contains(type)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "数据库类型仅支持 mysql / mariadb / postgresql / sqlserver / oracle");
        }
        return type;
    }

    public static int defaultPort(String dbType) {
        return switch (normalizeDbType(dbType)) {
            case DB_POSTGRESQL -> 5432;
            case DB_SQLSERVER -> 1433;
            case DB_ORACLE -> 1521;
            case DB_MARIADB -> 3306;
            default -> 3306;
        };
    }

    public static String driverClassName(String dbType) {
        return switch (normalizeDbType(dbType)) {
            case DB_POSTGRESQL -> "org.postgresql.Driver";
            case DB_SQLSERVER -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case DB_ORACLE -> "oracle.jdbc.OracleDriver";
            case DB_MARIADB, DB_MYSQL -> "com.mysql.cj.jdbc.Driver";
            default -> "com.mysql.cj.jdbc.Driver";
        };
    }

    public static String jdbcUrl(JdbcEndpoint endpoint) {
        return switch (endpoint.dbType()) {
            case DB_POSTGRESQL -> "jdbc:postgresql://" + endpoint.host() + ":" + endpoint.port()
                    + "/" + endpoint.dbName();
            case DB_SQLSERVER -> "jdbc:sqlserver://" + endpoint.host() + ":" + endpoint.port()
                    + ";databaseName=" + endpoint.dbName() + ";encrypt=false;trustServerCertificate=true";
            case DB_ORACLE -> "jdbc:oracle:thin:@" + endpoint.host() + ":" + endpoint.port()
                    + ":" + endpoint.dbName();
            case DB_MARIADB -> "jdbc:mysql://" + endpoint.host() + ":" + endpoint.port() + "/" + endpoint.dbName()
                    + commonMysqlParams();
            default -> "jdbc:mysql://" + endpoint.host() + ":" + endpoint.port() + "/" + endpoint.dbName()
                    + commonMysqlParams();
        };
    }

    public static String validationQuery(String dbType) {
        return DB_ORACLE.equals(normalizeDbType(dbType)) ? "SELECT 1 FROM DUAL" : "SELECT 1";
    }

    public static String displayUrl(JdbcEndpoint endpoint) {
        if (endpoint == null || !StringUtils.hasText(endpoint.host())) {
            return (endpoint == null ? DB_MYSQL : endpoint.dbType()) + "://";
        }
        String db = StringUtils.hasText(endpoint.dbName()) ? "/" + endpoint.dbName() : "";
        return endpoint.dbType() + "://" + endpoint.host() + ":" + endpoint.port() + db;
    }

    private static String commonMysqlParams() {
        return "?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
                + "&allowMultiQueries=false&autoReconnect=false";
    }

    public static Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private static String trim(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null || !StringUtils.hasText(String.valueOf(raw))) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public record JdbcEndpoint(String dbType, String host, int port, String dbName, String username) {
        public String display() {
            return displayUrl(this);
        }
    }
}
