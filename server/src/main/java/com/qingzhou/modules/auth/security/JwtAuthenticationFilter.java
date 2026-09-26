package com.qingzhou.modules.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.R;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.constant.RedisKeys;
import com.qingzhou.infra.redis.RedisOps;
import com.qingzhou.modules.auth.entity.SysUser;
import com.qingzhou.modules.auth.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Duration STATUS_CACHE_TTL = Duration.ofSeconds(30);

    private final JwtTokenService jwtTokenService;
    private final SysUserMapper sysUserMapper;
    private final RedisOps redisOps;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            AuthUserPrincipal principal = jwtTokenService.parse(token);
            if (principal != null && !isUserActive(principal.getId())) {
                writeUnauthorized(response, "账号已停用或登录已失效，请重新登录");
                return;
            }
            if (principal != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (requiresAuth(request)) {
                writeUnauthorized(response, "登录已失效，请重新登录");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isUserActive(Long userId) {
        if (userId == null) {
            return false;
        }
        String key = RedisKeys.userStatus(userId);
        String cached = redisOps.get(key);
        if (StringUtils.hasText(cached)) {
            return "1".equals(cached);
        }
        SysUser user = sysUserMapper.selectById(userId);
        boolean active = user != null
                && (user.getDeleted() == null || user.getDeleted() == 0)
                && user.getStatus() != null
                && user.getStatus() == 1;
        redisOps.set(key, active ? "1" : "0", STATUS_CACHE_TTL);
        return active;
    }

    private boolean requiresAuth(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/")
                && !path.startsWith("/api/auth/login")
                && !path.startsWith("/api/auth/bootstrap")
                && !path.equals("/api/health");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), R.fail(ResultCode.UNAUTHORIZED, message));
    }
}
