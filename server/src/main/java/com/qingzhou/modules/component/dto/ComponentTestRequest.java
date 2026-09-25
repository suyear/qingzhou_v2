package com.qingzhou.modules.component.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ComponentTestRequest {

    /** 企业微信组件可选；不传则使用全局凭证 */
    private Long credentialId;

    /** 试连通入参，按 query/body Schema 填 */
    private Map<String, Object> params = new LinkedHashMap<>();
}
