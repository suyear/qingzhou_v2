package com.qingzhou.modules.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.R;
import com.qingzhou.common.api.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 强制改密期间仅允许查询当前用户、改密与退出。
 */
@Component
@RequiredArgsConstructor
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        AuthUserPrincipal principal = AuthContext.current().orElse(null);
        if (principal != null && principal.isMustChangePassword() && !allowedWhileMustChange(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    R.fail(ResultCode.FORBIDDEN, "请先修改初始密码后再继续使用"));
            SecurityContextHolder.clearContext();
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean allowedWhileMustChange(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method) && "/api/auth/me".equals(path)) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && ("/api/auth/change-password".equals(path)
                || "/api/auth/logout".equals(path))) {
            return true;
        }
        return false;
    }
}
