package com.qingzhou.modules.openapi.dto;

import com.qingzhou.modules.openapi.entity.OpenapiApp;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OpenapiAppListVO extends OpenapiApp {

    /** 已授权组合接口服务（工作流）数量 */
    private int grantedWorkflowCount;

    /** 已授权接口服务（组件）数量 */
    private int grantedComponentCount;

    /** 兼容：授权总数 */
    private int grantedCount;
}
