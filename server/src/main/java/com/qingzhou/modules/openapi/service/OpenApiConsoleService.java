package com.qingzhou.modules.openapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.crypto.SignatureUtil;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.common.json.Jsons;
import com.qingzhou.modules.execution.engine.HttpCallResult;
import com.qingzhou.modules.execution.engine.NodeHttpInvoker;
import com.qingzhou.modules.openapi.dto.OpenapiInvokePreviewVO;
import com.qingzhou.modules.openapi.dto.OpenapiInvokeRequest;
import com.qingzhou.modules.openapi.dto.OpenapiInvokeResultVO;
import com.qingzhou.modules.openapi.entity.OpenapiApp;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenApiConsoleService {

    private final OpenapiAppService openapiAppService;
    private final AesEncryptor aesEncryptor;
    private final Jsons jsons;
    private final ObjectMapper objectMapper;
    private final NodeHttpInvoker nodeHttpInvoker;

    @Value("${server.port:18080}")
    private int serverPort;

    @Value("${qingzhou.openapi.public-base-url:}")
    private String publicBaseUrl;

    public OpenapiInvokePreviewVO preview(Long appId, OpenapiInvokeRequest request) {
        OpenapiApp app = requireApp(appId);
        openapiAppService.assertGranted(appId, request.getWorkflowCode());
        String body = jsons.toJson(request.getInput() == null ? Map.of() : request.getInput());
        if (body == null) {
            body = "{}";
        }
        String timestamp = SignatureUtil.currentTimestamp();
        String nonce = SignatureUtil.randomNonce();
        String secret = aesEncryptor.decrypt(app.getAppSecretCipher());
        String signature = SignatureUtil.sign(body, timestamp, nonce, secret);

        String path = "/openapi/v1/workflows/" + request.getWorkflowCode().trim() + "/execute";
        String url = baseUrl() + path;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put(SignatureUtil.HEADER_APP_KEY, app.getAppKey());
        headers.put(SignatureUtil.HEADER_TIMESTAMP, timestamp);
        headers.put(SignatureUtil.HEADER_NONCE, nonce);
        headers.put(SignatureUtil.HEADER_SIGNATURE, signature);

        OpenapiInvokePreviewVO vo = new OpenapiInvokePreviewVO();
        vo.setMethod("POST");
        vo.setUrl(url);
        vo.setPath(path);
        vo.setHeaders(headers);
        vo.setBody(body);
        vo.setCurl(toCurl(url, headers, body));
        vo.setTip("签名按原始 body 字节计算。json.dumps 默认带空格会导致签名不匹配，请用紧凑 JSON。");
        return vo;
    }

    public OpenapiInvokeResultVO invoke(Long appId, OpenapiInvokeRequest request) {
        OpenapiInvokePreviewVO preview = preview(appId, request);
        HttpCallResult result = nodeHttpInvoker.invoke(
                preview.getMethod(),
                URI.create(preview.getUrl()),
                preview.getHeaders(),
                preview.getBody(),
                20000);
        OpenapiInvokeResultVO vo = new OpenapiInvokeResultVO();
        vo.setPreview(preview);
        vo.setHttpStatus(result.status() == 0 ? null : result.status());
        if (result.error() != null) {
            vo.setResponse(Map.of("message", result.error(), "timeout", result.timeout()));
            return vo;
        }
        vo.setResponse(parseJson(result.body()));
        return vo;
    }

    private OpenapiApp requireApp(Long appId) {
        OpenapiApp app = openapiAppService.getById(appId);
        if (app == null) {
            throw new BizException("OpenAPI 应用不存在");
        }
        if (!Integer.valueOf(1).equals(app.getStatus())) {
            throw new BizException("应用已停用");
        }
        return app;
    }

    private String baseUrl() {
        if (StringUtils.hasText(publicBaseUrl)) {
            String base = publicBaseUrl.trim();
            return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        }
        return "http://127.0.0.1:" + serverPort;
    }

    private Object parseJson(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return body;
        }
    }

    private static String toCurl(String url, Map<String, String> headers, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("curl -sS -X POST '").append(url).append("' \\\n");
        headers.forEach((key, value) -> sb.append("  -H '").append(key).append(": ").append(value).append("' \\\n"));
        sb.append("  --data-raw '").append(escapeSingleQuotes(body)).append("'");
        return sb.toString();
    }

    private static String escapeSingleQuotes(String value) {
        return value == null ? "" : value.replace("'", "'\\''");
    }
}
