package com.qingzhou.modules.execution.engine.sql;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 数据库组件 SQL 安全底线：单语句、禁止危险关键字、按组件只读/读写约束。
 */
public final class SqlGuard {

    public static final String MODE_READ = "READ";
    public static final String MODE_WRITE = "WRITE";

    private static final List<Pattern> DENY = List.of(
            Pattern.compile("(?i)\\bINTO\\s+OUTFILE\\b"),
            Pattern.compile("(?i)\\bINTO\\s+DUMPFILE\\b"),
            Pattern.compile("(?i)\\bLOAD_FILE\\s*\\("),
            Pattern.compile("(?i)\\bLOAD\\s+DATA\\b"),
            Pattern.compile("(?i)\\bSLEEP\\s*\\("),
            Pattern.compile("(?i)\\bBENCHMARK\\s*\\("),
            Pattern.compile("(?i)\\bGET_LOCK\\s*\\("),
            Pattern.compile("(?i)\\bDROP\\b"),
            Pattern.compile("(?i)\\bALTER\\b"),
            Pattern.compile("(?i)\\bTRUNCATE\\b"),
            Pattern.compile("(?i)\\bCREATE\\b"),
            Pattern.compile("(?i)\\bGRANT\\b"),
            Pattern.compile("(?i)\\bREVOKE\\b"),
            Pattern.compile("(?i)\\bCALL\\b"),
            Pattern.compile("(?i)\\bEXECUTE\\b"),
            Pattern.compile("(?i)\\bPREPARE\\b"),
            Pattern.compile("(?i)\\bSET\\s+GLOBAL\\b"),
            Pattern.compile("(?i)\\bLOCK\\s+TABLES\\b"),
            Pattern.compile("(?i)\\bUNLOCK\\s+TABLES\\b")
    );

    private SqlGuard() {
    }

    public static BoundSql validate(String sql, String accessMode) {
        BoundSql bound = NamedSqlParser.parse(sql);
        String mode = normalizeMode(accessMode);
        denyDangerous(bound.jdbcSql());
        if (bound.kind() == SqlKind.UNKNOWN) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "不支持的 SQL 类型。只读允许 SELECT/SHOW/DESCRIBE/EXPLAIN；读写额外允许 INSERT/UPDATE/DELETE/REPLACE");
        }
        if (MODE_READ.equals(mode) && bound.kind() == SqlKind.UPDATE) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前组件为只读，不允许执行 INSERT/UPDATE/DELETE");
        }
        return bound;
    }

    public static String normalizeMode(String accessMode) {
        if (!StringUtils.hasText(accessMode)) {
            return MODE_READ;
        }
        String mode = accessMode.trim().toUpperCase(Locale.ROOT);
        if (!MODE_READ.equals(mode) && !MODE_WRITE.equals(mode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "访问模式仅支持 READ / WRITE");
        }
        return mode;
    }

    public static String methodOf(BoundSql bound) {
        return bound.kind() == SqlKind.UPDATE ? "UPDATE" : "QUERY";
    }

    private static void denyDangerous(String jdbcSql) {
        for (Pattern pattern : DENY) {
            if (pattern.matcher(jdbcSql).find()) {
                throw new BizException(ResultCode.BAD_REQUEST, "SQL 包含不允许的语句或函数，请检查后重试");
            }
        }
    }
}
