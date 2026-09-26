package com.qingzhou.modules.execution.engine;

import com.qingzhou.modules.execution.engine.auth.DigestAuthHandler;
import com.qingzhou.modules.execution.engine.auth.MtlsHttpClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 节点 HTTP 调用。支持 Digest 二次挑战与 mTLS 自定义客户端。
 * 出站前校验 SSRF；不跟随重定向；响应体硬上限防 OOM。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeHttpInvoker {

    public static final int DEFAULT_BODY_LIMIT_BYTES = 2 * 1024 * 1024;

    private final MtlsHttpClientFactory mtlsHttpClientFactory;

    @Value("${qingzhou.http.allow-private-network:false}")
    private boolean allowPrivateNetwork;

    @Value("${qingzhou.http.max-response-bytes:2097152}")
    private int maxResponseBytes;

    private final HttpClient defaultClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public HttpCallResult invoke(String method, URI uri, Map<String, String> headers, String body, int timeoutMs) {
        return invoke(method, uri, headers, body, timeoutMs, null);
    }

    public HttpCallResult invoke(
            String method,
            URI uri,
            Map<String, String> headers,
            String body,
            int timeoutMs,
            OutboundAuthResolver.ResolvedAuth auth) {
        try {
            OutboundUrlGuard.validate(uri, allowPrivateNetwork);
        } catch (com.qingzhou.common.exception.BizException ex) {
            return new HttpCallResult(0, null, false, ex.getMessage(), Map.of());
        }
        HttpClient client = defaultClient;
        if (auth != null && auth.mtlsCredentialId() != null) {
            client = mtlsHttpClientFactory.getClient(auth.mtlsCredentialId());
        }
        HttpCallResult first = send(client, method, uri, headers, body, timeoutMs);
        if (auth != null && auth.digestAuth() && first.status() == 401) {
            String www = extractWwwAuthenticate(first);
            if (DigestAuthHandler.isDigestChallenge(www)) {
                String path = uri.getRawPath() == null ? "/" : uri.getRawPath();
                if (StringUtils.hasText(uri.getRawQuery())) {
                    path = path + "?" + uri.getRawQuery();
                }
                String authorization = DigestAuthHandler.buildAuthorization(
                        www, auth.digestUsername(), auth.digestPassword(), method, path);
                Map<String, String> retryHeaders = new LinkedHashMap<>();
                if (headers != null) {
                    retryHeaders.putAll(headers);
                }
                retryHeaders.put("Authorization", authorization);
                return send(client, method, uri, retryHeaders, body, timeoutMs);
            }
        }
        return first;
    }

    private HttpCallResult send(
            HttpClient client,
            String method,
            URI uri,
            Map<String, String> headers,
            String body,
            int timeoutMs) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(Math.max(1000, timeoutMs)));
            if (headers != null) {
                headers.forEach((k, v) -> {
                    if (StringUtils.hasText(k) && v != null) {
                        builder.header(k, v);
                    }
                });
            }
            String verb = method == null ? "GET" : method.toUpperCase();
            if ("GET".equals(verb) || "DELETE".equals(verb)) {
                builder.method(verb, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", headers != null && headers.containsKey("Content-Type")
                        ? headers.get("Content-Type") : "application/json");
                builder.method(verb, HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
            }
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int limit = maxResponseBytes > 0 ? maxResponseBytes : DEFAULT_BODY_LIMIT_BYTES;
            ReadBody read = readLimited(response.body(), limit);
            if (read.truncated()) {
                log.warn("节点 HTTP 响应体超限 uri={} limit={}", safeUri(uri), limit);
                return new HttpCallResult(response.statusCode(), read.text(), false,
                        "第三方响应体超过上限 " + limit + " 字节", response.headers().map());
            }
            return new HttpCallResult(response.statusCode(), read.text(), false, null, response.headers().map());
        } catch (java.net.http.HttpTimeoutException timeout) {
            log.warn("节点 HTTP 超时 uri={}", safeUri(uri));
            return new HttpCallResult(0, null, true, "第三方接口超时", Map.of());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new HttpCallResult(0, null, true, "调用被中断", Map.of());
        } catch (Exception ex) {
            String safe = HttpUrlSupport.maskSecret(ex.getMessage());
            log.warn("节点 HTTP 失败 uri={} msg={}", safeUri(uri), safe);
            return new HttpCallResult(0, null, false, safe, Map.of());
        }
    }

    public static ReadBody readLimited(InputStream in, int limit) throws Exception {
        if (in == null) {
            return new ReadBody("", false);
        }
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = stream.read(buf)) >= 0) {
                if (n == 0) {
                    continue;
                }
                if (total + n > limit) {
                    int keep = Math.max(0, limit - total);
                    if (keep > 0) {
                        out.write(buf, 0, keep);
                    }
                    // drain remainder to free connection, but mark truncated
                    while (stream.read(buf) >= 0) {
                        // discard
                    }
                    return new ReadBody(out.toString(StandardCharsets.UTF_8), true);
                }
                out.write(buf, 0, n);
                total += n;
            }
            return new ReadBody(out.toString(StandardCharsets.UTF_8), false);
        }
    }

    private static String extractWwwAuthenticate(HttpCallResult result) {
        if (result == null || result.responseHeaders() == null) {
            return null;
        }
        Optional<List<String>> values = result.responseHeaders().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase("WWW-Authenticate"))
                .map(Map.Entry::getValue)
                .findFirst();
        return values.filter(list -> !list.isEmpty()).map(list -> list.get(0)).orElse(null);
    }

    public record ReadBody(String text, boolean truncated) {
    }

    private static String safeUri(URI uri) {
        return HttpUrlSupport.maskSecret(uri == null ? null : uri.toString());
    }
}
