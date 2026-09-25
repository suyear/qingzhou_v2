package com.qingzhou.modules.execution.engine;

import com.qingzhou.modules.execution.engine.auth.DigestAuthHandler;
import com.qingzhou.modules.execution.engine.auth.MtlsHttpClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeHttpInvoker {

    private final MtlsHttpClientFactory mtlsHttpClientFactory;

    private final HttpClient defaultClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
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
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpCallResult(response.statusCode(), response.body(), false, null, response.headers().map());
        } catch (java.net.http.HttpTimeoutException timeout) {
            log.warn("节点 HTTP 超时 uri={}", uri);
            return new HttpCallResult(0, null, true, "第三方接口超时", Map.of());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new HttpCallResult(0, null, true, "调用被中断", Map.of());
        } catch (Exception ex) {
            log.warn("节点 HTTP 失败 uri={} msg={}", uri, ex.getMessage());
            return new HttpCallResult(0, null, false, ex.getMessage(), Map.of());
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
}
