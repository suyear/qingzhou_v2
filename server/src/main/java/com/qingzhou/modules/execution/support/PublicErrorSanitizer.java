package com.qingzhou.modules.execution.support;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 开放 API 对外错误文案：去掉节点名、SQL/驱动堆栈等内部细节。
 * 控制台执行记录仍保留原始 errorMsg。
 */
public final class PublicErrorSanitizer {

    private PublicErrorSanitizer() {
    }

    public static String forOpenApi(String status, String raw) {
        if ("TIMEOUT".equalsIgnoreCase(status) || FailureCategory.looksLikeTimeout(raw)) {
            return "执行超时";
        }
        if (!StringUtils.hasText(raw)) {
            return "执行失败";
        }
        String text = raw.replaceFirst("^节点\\s.+?\\s失败:\\s*", "").trim();
        if (!StringUtils.hasText(text) || looksInternal(text)) {
            return "执行失败";
        }
        if (text.length() > 200) {
            return text.substring(0, 200);
        }
        return text;
    }

    private static boolean looksInternal(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("exception")
                || lower.contains("java.")
                || lower.contains("sql")
                || lower.contains("syntax")
                || lower.contains("jdbc")
                || lower.contains("stack")
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;
    }
}
