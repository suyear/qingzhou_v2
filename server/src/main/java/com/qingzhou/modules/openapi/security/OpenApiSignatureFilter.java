package com.qingzhou.modules.openapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.R;
import com.qingzhou.common.crypto.SignatureUtil;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.common.web.CachedBodyHttpServletRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 开放平台网关：校验 X-App-Key / X-Timestamp / X-Nonce / X-Signature，并用 Redis 做 Nonce 去重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiSignatureFilter extends OncePerRequestFilter {

    private final OpenApiAuthenticator authenticator;
    private final OpenApiAccessGuard accessGuard;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return uri == null || !uri.startsWith("/openapi/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
        try {
            String appKey = wrapped.getHeader(SignatureUtil.HEADER_APP_KEY);
            String timestamp = wrapped.getHeader(SignatureUtil.HEADER_TIMESTAMP);
            String nonce = wrapped.getHeader(SignatureUtil.HEADER_NONCE);
            String signature = wrapped.getHeader(SignatureUtil.HEADER_SIGNATURE);
            var app = authenticator.authenticate(appKey, timestamp, nonce, signature, wrapped.bodyAsString());
            accessGuard.check(app, wrapped);
            wrapped.setAttribute(OpenApiAuthenticator.ATTR_APP, app);
            filterChain.doFilter(wrapped, response);
        } catch (BizException ex) {
            writeError(response, ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("OpenAPI 网关异常", ex);
            writeError(response, 401, "签名校验失败");
        }
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(httpStatus(code));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(code, message)));
    }

    private static int httpStatus(int code) {
        if (code == 429) {
            return 429;
        }
        if (code == 403) {
            return 403;
        }
        if (code == 400) {
            return 400;
        }
        return HttpServletResponse.SC_UNAUTHORIZED;
    }
}
