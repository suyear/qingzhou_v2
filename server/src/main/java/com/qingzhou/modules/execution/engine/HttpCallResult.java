package com.qingzhou.modules.execution.engine;

import java.util.List;
import java.util.Map;

public record HttpCallResult(
        int status,
        String body,
        boolean timeout,
        String error,
        Map<String, List<String>> responseHeaders
) {

    public HttpCallResult(int status, String body, boolean timeout, String error) {
        this(status, body, timeout, error, Map.of());
    }

    public boolean success() {
        return !timeout && error == null && status >= 200 && status < 300;
    }
}
