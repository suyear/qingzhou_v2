package com.qingzhou.modules.execution.engine;

import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.credential.entity.Credential;
import com.qingzhou.modules.credential.service.CredentialService;
import com.qingzhou.modules.credential.support.JdbcCredentialSupport;
import com.qingzhou.modules.execution.engine.sql.BoundSql;
import com.qingzhou.modules.execution.engine.sql.SqlKind;
import com.qingzhou.modules.execution.engine.sql.SqlParamBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NodeJdbcInvoker {

    private static final int CELL_LIMIT = 200;

    private final DatasourcePoolManager datasourcePoolManager;
    private final CredentialService credentialService;

    public DbCallResult invoke(DatabaseComponentSupport.DatabaseSpec spec, Map<String, Object> payload, int timeoutMs) {
        BoundSql bound = spec.bound();
        Credential credential = credentialService.getById(spec.datasourceId());
        String displayUrl = JdbcCredentialSupport.displayUrl(JdbcCredentialSupport.from(credential));
        int timeoutSec = Math.max(1, (Math.max(timeoutMs, 1000) + 999) / 1000);
        try (Connection connection = datasourcePoolManager.getConnection(spec.datasourceId());
             PreparedStatement statement = connection.prepareStatement(bound.jdbcSql())) {
            statement.setQueryTimeout(timeoutSec);
            if (bound.kind() == SqlKind.QUERY) {
                statement.setMaxRows(spec.maxRows() + 1);
            }
            SqlParamBinder.bind(statement, bound, payload);
            if (bound.kind() == SqlKind.QUERY) {
                try (ResultSet rs = statement.executeQuery()) {
                    return readQuery(rs, spec.maxRows(), displayUrl);
                }
            }
            int affected = statement.executeUpdate();
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("affectedRows", affected);
            output.put("rowCount", 0);
            output.put("columns", List.of());
            output.put("rows", List.of());
            output.put("truncated", false);
            return DbCallResult.okUpdate(displayUrl, affected, output);
        } catch (BizException ex) {
            return DbCallResult.fail(displayUrl, false, ex.getMessage());
        } catch (SQLTimeoutException timeout) {
            log.warn("数据库组件超时 datasourceId={}", spec.datasourceId());
            return DbCallResult.fail(displayUrl, true, "数据库语句超时（" + timeoutSec + "s）");
        } catch (SQLException ex) {
            log.warn("数据库组件失败 datasourceId={} sqlState={} msg={}",
                    spec.datasourceId(), ex.getSQLState(), ex.getMessage());
            return DbCallResult.fail(displayUrl, isTimeout(ex), readableSqlError(ex));
        } catch (Exception ex) {
            return DbCallResult.fail(displayUrl, false, ex.getMessage());
        }
    }

    private DbCallResult readQuery(ResultSet rs, int maxRows, String displayUrl) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            columns.add(label == null || label.isBlank() ? meta.getColumnName(i) : label);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(columns.get(i - 1), readCell(rs, i, meta.getColumnType(i)));
            }
            rows.add(row);
        }
        List<Map<String, Object>> preview = rows.size() > DatabaseComponentSupport.PREVIEW_ROWS
                ? rows.subList(0, DatabaseComponentSupport.PREVIEW_ROWS)
                : rows;
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("rowCount", rows.size());
        output.put("affectedRows", 0);
        output.put("columns", columns);
        output.put("rows", rows);
        output.put("truncated", truncated);
        Map<String, Object> logBody = new LinkedHashMap<>();
        logBody.put("rowCount", rows.size());
        logBody.put("affectedRows", 0);
        logBody.put("columns", columns);
        logBody.put("preview", preview);
        logBody.put("truncated", truncated || rows.size() > DatabaseComponentSupport.PREVIEW_ROWS);
        return DbCallResult.okQuery(displayUrl, rows.size(), columns, logBody, output, truncated);
    }

    private Object readCell(ResultSet rs, int index, int type) throws SQLException {
        if (type == Types.BLOB || type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY) {
            Blob blob = rs.getBlob(index);
            if (blob == null || rs.wasNull()) {
                return null;
            }
            return "<binary " + blob.length() + "B>";
        }
        if (type == Types.CLOB || type == Types.NCLOB || type == Types.LONGVARCHAR) {
            Clob clob = rs.getClob(index);
            if (clob == null || rs.wasNull()) {
                return null;
            }
            String text = clob.getSubString(1, (int) Math.min(clob.length(), CELL_LIMIT));
            if (clob.length() > CELL_LIMIT) {
                return text + "...";
            }
            return text;
        }
        Object raw = rs.getObject(index);
        if (raw == null || rs.wasNull()) {
            return null;
        }
        if (raw instanceof java.sql.Timestamp ts) {
            return OffsetDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault()).toString();
        }
        if (raw instanceof java.sql.Date date) {
            return date.toString();
        }
        if (raw instanceof java.sql.Time time) {
            return time.toString();
        }
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw instanceof byte[] bytes) {
            return "<binary " + bytes.length + "B>";
        }
        String text = String.valueOf(raw);
        if (text.length() > CELL_LIMIT) {
            return text.substring(0, CELL_LIMIT - 3) + "...";
        }
        return raw instanceof Number || raw instanceof Boolean ? raw : text;
    }

    private static boolean isTimeout(SQLException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return ex instanceof SQLTimeoutException || message.contains("timeout") || message.contains("timed out");
    }

    private static String readableSqlError(SQLException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "数据库执行失败";
        }
        String compact = message.replace('\n', ' ').trim();
        if (compact.length() > 400) {
            return compact.substring(0, 397) + "...";
        }
        return compact;
    }
}
