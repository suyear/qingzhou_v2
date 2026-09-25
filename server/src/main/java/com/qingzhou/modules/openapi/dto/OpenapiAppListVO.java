package com.qingzhou.modules.openapi.dto;

import com.qingzhou.modules.openapi.entity.OpenapiApp;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OpenapiAppListVO extends OpenapiApp {

    /** 已授权工作流数量 */
    private int grantedCount;
}
