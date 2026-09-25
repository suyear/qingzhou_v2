package com.qingzhou.infra.wecom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WecomTokenResponse(
        Integer errcode,
        String errmsg,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Integer expiresIn
) {
    public boolean success() {
        return errcode == null || errcode == 0;
    }
}
