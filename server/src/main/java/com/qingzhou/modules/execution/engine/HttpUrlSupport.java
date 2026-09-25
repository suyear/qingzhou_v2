package com.qingzhou.modules.execution.engine;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.component.entity.ApiComponent;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpUrlSupport {

    public static final Set<String> META_KEYS = Set.of(
            "componentId", "componentCode", "componentName", "httpMethod",
            "urlTemplate", "urlPath", "timeoutMs", "retryTimes", "retryIntervalMs", "requiredParams",
            "provider", "category", "sqlPreview"
    );

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final int BODY_LIMIT = 4000;

    private HttpUrlSupport() {
    }

    public static boolean needsAccessToken(ApiComponent component) {
        if (component == null) {
            return false;
        }
        String extra = String.valueOf(component.getExtraConfig());
        return (component.getUrlTemplate() != null && component.getUrlTemplate().contains("access_token"))
                || extra.contains("needAccessToken")
                || extra.contains("\"type\":\"wecom\"")
                || extra.contains("\"type\": \"wecom\"");
    }

    public static String renderUrl(String template, Map<String, Object> payload, String accessToken) {
        if (template == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "组件 URL 为空");
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value;
            if ("access_token".equals(key)) {
                if (!StringUtils.hasText(accessToken)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "缺少企业微信 AccessToken");
                }
                value = accessToken;
            } else {
                Object found = payload == null ? null : payload.get(key);
                value = found == null ? "" : String.valueOf(found);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static URI buildUri(String url, String method, Map<String, Object> payload) {
        if (!"GET".equalsIgnoreCase(method) || payload == null || payload.isEmpty()) {
            return URI.create(url);
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        payload.forEach((k, v) -> {
            if (v != null && !META_KEYS.contains(k)) {
                builder.replaceQueryParam(k, v);
            }
        });
        return builder.encode().build(true).toUri();
    }

    public static String maskSecret(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(?i)(access_token=)[^&]*", "$1***");
    }

    public static String truncateBody(String body) {
        if (body == null) {
            return null;
        }
        if (body.length() <= BODY_LIMIT) {
            return body;
        }
        return body.substring(0, BODY_LIMIT) + "...(truncated)";
    }
}
