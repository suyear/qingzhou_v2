package com.qingzhou.modules.openapi.dto;

import lombok.Data;

@Data
public class OpenapiInvokeResultVO {

    private OpenapiInvokePreviewVO preview;
    private Integer httpStatus;
    private Object response;
}
