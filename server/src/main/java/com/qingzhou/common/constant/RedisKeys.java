package com.qingzhou.common.constant;

/**
 * Redis Key 约定。冒号分段，便于 SCAN / 运维排查。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 企业微信 AccessToken：qz:wecom:token:{credentialId} */
    public static String wecomToken(Long credentialId) {
        return "qz:wecom:token:" + credentialId;
    }

    /** Token 刷新分布式锁：qz:wecom:token:lock:{credentialId} */
    public static String wecomTokenLock(Long credentialId) {
        return "qz:wecom:token:lock:" + credentialId;
    }

    /** OpenAPI Nonce 去重：qz:openapi:nonce:{appKey}:{nonce} */
    public static String openApiNonce(String appKey, String nonce) {
        return "qz:openapi:nonce:" + appKey + ":" + nonce;
    }

    /** OpenAPI 应用缓存：qz:openapi:app:{appKey} */
    public static String openApiApp(String appKey) {
        return "qz:openapi:app:" + appKey;
    }

    /** OpenAPI QPS 计数：qz:openapi:qps:{appKey}:{epochSecond} */
    public static String openApiQps(String appKey, long epochSecond) {
        return "qz:openapi:qps:" + appKey + ":" + epochSecond;
    }

    /** OAuth2 客户端凭证 AccessToken：qz:oauth2:token:{credentialId} */
    public static String oauth2Token(Long credentialId) {
        return "qz:oauth2:token:" + credentialId;
    }
}
