package com.qingzhou.modules.execution.support;

import org.springframework.util.StringUtils;

/**
 * 运行失败分类：列表 / 链路共用，避免前后端各写一套规则。
 */
public final class FailureCategory {

    public static final String TIMEOUT = "TIMEOUT";
    public static final String AUTH = "AUTH";
    public static final String PARAM = "PARAM";
    public static final String UPSTREAM = "UPSTREAM";
    public static final String BUSINESS = "BUSINESS";
    public static final String UNKNOWN = "UNKNOWN";

    private FailureCategory() {
    }

    public static String classify(String status, String errorMsg) {
        if ("SUCCESS".equals(status) || "RUNNING".equals(status) || "PENDING".equals(status)) {
            return null;
        }
        if ("TIMEOUT".equals(status)) {
            return TIMEOUT;
        }
        if (!StringUtils.hasText(errorMsg) && !"FAILED".equals(status)) {
            return null;
        }
        String msg = errorMsg == null ? "" : errorMsg.toLowerCase();
        String raw = errorMsg == null ? "" : errorMsg;
        if (looksLikeTimeout(msg, raw)) {
            return TIMEOUT;
        }
        if (containsAny(msg, "签名", "unauthorized", "401", "403", "鉴权", "token", "secret", "白名单", "未授权", "forbidden")) {
            return AUTH;
        }
        if (containsAny(msg, "缺少", "必填", "入参", "参数", "bad request", "400", "校验", "不能为空")) {
            return PARAM;
        }
        if (containsAny(msg, "连接", "网络", "502", "503", "504", "5xx", "第三方", "http ", "econn", "refused", "unreachable")) {
            return UPSTREAM;
        }
        if (!StringUtils.hasText(errorMsg)) {
            return UNKNOWN;
        }
        return BUSINESS;
    }

    public static String label(String category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case TIMEOUT -> "超时";
            case AUTH -> "鉴权";
            case PARAM -> "入参";
            case UPSTREAM -> "上游";
            case BUSINESS -> "业务";
            case UNKNOWN -> "未知";
            default -> category;
        };
    }

    public static boolean looksLikeTimeout(String errorMsg) {
        if (!StringUtils.hasText(errorMsg)) {
            return false;
        }
        return looksLikeTimeout(errorMsg.toLowerCase(), errorMsg);
    }

    private static boolean looksLikeTimeout(String lower, String raw) {
        return lower.contains("timeout")
                || raw.contains("超时")
                || lower.contains("timed out")
                || lower.contains("504");
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle.toLowerCase()) || haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
