package com.qingzhou.modules.execution.engine;

import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.component.entity.ApiComponent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpUrlSupportTest {

    @Test
    void renderUrlSubstitutesTokenAndPayload() {
        String url = HttpUrlSupport.renderUrl(
                "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=${access_token}&userid=${userid}",
                Map.of("userid", "zhangsan"),
                "TOKEN");
        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=TOKEN&userid=zhangsan", url);
    }

    @Test
    void renderUrlRejectsMissingToken() {
        assertThrows(BizException.class, () -> HttpUrlSupport.renderUrl(
                "https://example.com?access_token=${access_token}", Map.of(), null));
    }

    @Test
    void maskSecretHidesAccessToken() {
        assertEquals(
                "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=***",
                HttpUrlSupport.maskSecret("https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=SECRET"));
    }

    @Test
    void buildUriAppendsGetQuery() {
        URI uri = HttpUrlSupport.buildUri("http://127.0.0.1:18080/api/health", "GET", Map.of("probe", "1"));
        assertTrue(uri.toString().contains("probe=1"));
    }

    @Test
    void needsAccessTokenFromUrlOrExtraConfig() {
        ApiComponent byUrl = new ApiComponent();
        byUrl.setUrlTemplate("https://qyapi.weixin.qq.com/cgi-bin/gettoken?access_token=${access_token}");
        assertTrue(HttpUrlSupport.needsAccessToken(byUrl));

        ApiComponent byConfig = new ApiComponent();
        byConfig.setUrlTemplate("https://example.com");
        byConfig.setExtraConfig("{\"needAccessToken\": true}");
        assertTrue(HttpUrlSupport.needsAccessToken(byConfig));

        ApiComponent plain = new ApiComponent();
        plain.setUrlTemplate("http://127.0.0.1:18080/api/health");
        assertFalse(HttpUrlSupport.needsAccessToken(plain));
    }
}
