package com.qingzhou.modules.openapi.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class OpenapiInvokePreviewVO {

    private String method;
    private String url;
    private String path;
    private Map<String, String> headers = new LinkedHashMap<>();
    private String body;
    private String curl;
    private String tip;
}
