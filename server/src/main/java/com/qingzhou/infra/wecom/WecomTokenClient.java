package com.qingzhou.infra.wecom;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 企业微信 gettoken 调用。corpsecret 只用于请求，禁止写入日志。
 */
@Slf4j
@Component
public class WecomTokenClient {

    private final RestClient restClient;
    private final String tokenUrl;
    private final int fetchRetry;

    public WecomTokenClient(
            RestClient restClient,
            @Value("${qingzhou.wecom.token-url}") String tokenUrl,
            @Value("${qingzhou.wecom.fetch-retry:2}") int fetchRetry) {
        this.restClient = restClient;
        this.tokenUrl = tokenUrl;
        this.fetchRetry = Math.max(0, fetchRetry);
    }

    public WecomTokenResponse fetch(String corpId, String corpSecret) {
        ResourceAccessException lastTimeout = null;
        RestClientException lastHttp = null;
        for (int attempt = 0; attempt <= fetchRetry; attempt++) {
            try {
                URI uri = UriComponentsBuilder.fromUriString(tokenUrl)
                        .queryParam("corpid", corpId)
                        .queryParam("corpsecret", corpSecret)
                        .encode()
                        .build()
                        .toUri();
                WecomTokenResponse response = restClient.get()
                        .uri(uri)
                        .retrieve()
                        .body(WecomTokenResponse.class);
                if (response == null) {
                    throw new BizException(ResultCode.THIRD_PARTY_ERROR, "企业微信返回空响应");
                }
                return response;
            } catch (ResourceAccessException timeout) {
                lastTimeout = timeout;
                log.warn("企业微信 gettoken 超时, attempt={}/{}", attempt + 1, fetchRetry + 1);
            } catch (RestClientException http) {
                lastHttp = http;
                log.warn("企业微信 gettoken HTTP 异常, attempt={}/{} msg={}",
                        attempt + 1, fetchRetry + 1, http.getMessage());
            }
        }
        if (lastTimeout != null) {
            throw new BizException(ResultCode.THIRD_PARTY_TIMEOUT, "企业微信 gettoken 超时");
        }
        throw new BizException(ResultCode.THIRD_PARTY_ERROR,
                "企业微信 gettoken 调用失败: " + (lastHttp == null ? "unknown" : lastHttp.getMessage()));
    }
}
