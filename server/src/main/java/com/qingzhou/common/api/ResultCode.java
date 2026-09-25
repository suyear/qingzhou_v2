package com.qingzhou.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "签名校验失败"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    SERVER_ERROR(500, "服务器内部错误"),
    THIRD_PARTY_TIMEOUT(504, "第三方接口超时"),
    THIRD_PARTY_ERROR(502, "第三方接口调用失败");

    private final int code;
    private final String message;
}
