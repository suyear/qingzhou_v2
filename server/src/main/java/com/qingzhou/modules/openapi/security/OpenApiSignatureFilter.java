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
import org.springframework.web.util.ContentCachingResponseWrapper;

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
    private final OpenApiIdempotencyStore idempotencyStore;
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
            String body = wrapped.bodyAsString();
            var app = authenticator.authenticate(appKey, timestamp, nonce, signature, body);
            accessGuard.check(app, wrapped);
            wrapped.setAttribute(OpenApiAuthenticator.ATTR_APP, app);
            String idemKey = OpenApiIdempotency.normalizeKey(wrapped.getHeader(OpenApiIdempotency.HEADER));
            if (idemKey != null) {
                OpenApiIdempotency.Packed replay = idempotencyStore.replay(
                        app.getAppKey(), wrapped.getMethod(), wrapped.getRequestURI(), idemKey, body);
                if (replay != null) {
                    writeReplay(response, replay);
                    return;
                }
            }
            ContentCachingResponseWrapper caching = new ContentCachingResponseWrapper(response);
            try {
                filterChain.doFilter(wrapped, caching);
                if (idemKey != null) {
                    byte[] captured = caching.getContentAsByteArray();
                    String responseBody = new String(captured, StandardCharsets.UTF_8);
                    idempotencyStore.store(
                            app.getAppKey(),
                            wrapped.getMethod(),
                            wrapped.getRequestURI(),
                            idemKey,
                            body,
                            caching.getStatus(),
                            responseBody);
                }
            } finally {
                caching.copyBodyToResponse();
            }
        } catch (BizException ex) {
            writeError(response, ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("OpenAPI 网关异常", ex);
            writeError(response, 401, "签名校验失败");
        }
    }

    private void writeReplay(HttpServletResponse response, OpenApiIdempotency.Packed replay) throws IOException {
        response.setStatus(replay.status() > 0 ? replay.status() : HttpServletResponse.SC_OK);
        response.setHeader(OpenApiIdempotency.REPLAY_HEADER, "true");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(replay.body() == null ? "" : replay.body());
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
        if (code == 400 || code == 409) {
            return code;
        }
        return HttpServletResponse.SC_UNAUTHORIZED;
    }
}
