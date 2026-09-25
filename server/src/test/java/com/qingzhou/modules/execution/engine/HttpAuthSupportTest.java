package com.qingzhou.modules.execution.engine;

import com.qingzhou.modules.component.entity.ApiComponent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpAuthSupportTest {

    @Test
    void resolveBearerAuth() {
        ApiComponent component = new ApiComponent();
        component.setExtraConfig("""
                {
                  "auth": { "type": "bearer", "bearerToken": "abc123" },
                  "headers": [{ "key": "X-Trace", "value": "1", "enabled": true }]
                }
                """);

        HttpAuthSupport.AuthResult result = HttpAuthSupport.resolve(component);
        assertEquals("Bearer abc123", result.headers().get("Authorization"));
        assertEquals("1", result.headers().get("X-Trace"));
        assertTrue(result.queryParams().isEmpty());
    }

    @Test
    void resolveApiKeyInQuery() {
        ApiComponent component = new ApiComponent();
        component.setExtraConfig("""
                {
                  "auth": {
                    "type": "apiKey",
                    "apiKeyName": "api_key",
                    "apiKeyValue": "secret",
                    "apiKeyIn": "query"
                  }
                }
                """);

        HttpAuthSupport.AuthResult result = HttpAuthSupport.resolve(component);
        assertEquals("secret", result.queryParams().get("api_key"));
        URI uri = HttpAuthSupport.appendQueryParams(URI.create("https://example.com/data"), result.queryParams());
        assertTrue(uri.toString().contains("api_key=secret"));
    }

    @Test
    void resolveBasicAuth() {
        ApiComponent component = new ApiComponent();
        component.setExtraConfig("""
                {
                  "auth": {
                    "type": "basic",
                    "basicUsername": "user",
                    "basicPassword": "pass"
                  }
                }
                """);

        HttpAuthSupport.AuthResult result = HttpAuthSupport.resolve(component);
        assertEquals("Basic dXNlcjpwYXNz", result.headers().get("Authorization"));
    }

    @Test
    void resolveWecomAuth() {
        ApiComponent component = new ApiComponent();
        component.setExtraConfig("{\"auth\":{\"type\":\"wecom\"}}");
        HttpAuthSupport.AuthResult result = HttpAuthSupport.resolve(component);
        assertTrue(result.needsWecomToken());
    }

    @Test
    void resolveCredentialTypeKeepsStaticHeaders() {
        ApiComponent component = new ApiComponent();
        component.setExtraConfig("""
                {
                  "auth": { "type": "credential", "credentialId": 12 },
                  "headers": [{ "key": "X-Trace", "value": "1", "enabled": true }]
                }
                """);
        HttpAuthSupport.AuthResult result = HttpAuthSupport.resolve(component);
        assertEquals("1", result.headers().get("X-Trace"));
        assertTrue(result.queryParams().isEmpty());
    }

    @Test
    void maskHeadersHidesSecrets() {
        Map<String, String> masked = HttpAuthSupport.maskHeaders(Map.of(
                "Authorization", "Bearer abc",
                "X-API-Key", "secret",
                "Accept", "application/json"
        ));
        assertEquals("***", masked.get("Authorization"));
        assertEquals("***", masked.get("X-API-Key"));
        assertEquals("application/json", masked.get("Accept"));
    }
}
