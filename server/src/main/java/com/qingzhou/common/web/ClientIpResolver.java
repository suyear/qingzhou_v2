package com.qingzhou.common.web;

import com.qingzhou.modules.openapi.security.IpRules;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析客户端 IP。仅在 trust-proxy=true（前置可信反向代理）时采信 X-Forwarded-For / X-Real-IP，
 * 否则一律用 remoteAddr，避免伪造头绕过 IP 白名单。
 */
@Component
public class ClientIpResolver {

    private final boolean trustProxy;

    public ClientIpResolver(@Value("${qingzhou.security.trust-proxy:false}") boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return IpRules.normalize(forwarded.split(",")[0]);
            }
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return IpRules.normalize(realIp);
            }
        }
        return IpRules.normalize(request.getRemoteAddr());
    }

    public boolean isTrustProxy() {
        return trustProxy;
    }
}
