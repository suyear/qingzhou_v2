package com.qingzhou.modules.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.R;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.modules.license.service.LicenseService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * License 过期时禁止写操作（导入 License / 登录 / 改密除外）。
 */
@Component
@RequiredArgsConstructor
public class LicenseWriteGuardFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final LicenseService licenseService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (isExempt(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (licenseService.isWriteBlocked()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    R.fail(ResultCode.FORBIDDEN, "License 已过期或无效，系统处于只读模式，请联系管理员导入有效 License"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isExempt(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/license/")
                || path.equals("/api/health")
                || path.startsWith("/openapi/");
    }
}
