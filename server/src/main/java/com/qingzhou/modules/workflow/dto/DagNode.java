package com.qingzhou.modules.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DagNode {

    /** 画布节点 ID，X6 cell.id */
    private String id;
    private Long componentId;
    private String componentCode;
    private String name;
    private Double x;
    private Double y;
    /** X6 节点扩展数据（入参表单值等） */
    private Map<String, Object> data;
}
