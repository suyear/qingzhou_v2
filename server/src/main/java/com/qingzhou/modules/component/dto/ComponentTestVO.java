package com.qingzhou.modules.component.dto;

import lombok.Data;

@Data
public class ComponentTestVO {

    private boolean success;
    private String message;
    private String requestMethod;
    private String requestUrl;
    private Integer httpStatus;
    private Long durationMs;
    private Integer wecomErrcode;
    private String responseBody;

    public static ComponentTestVO fail(String message) {
        ComponentTestVO vo = new ComponentTestVO();
        vo.setSuccess(false);
        vo.setMessage(message);
        return vo;
    }
}
