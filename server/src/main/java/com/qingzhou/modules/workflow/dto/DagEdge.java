package com.qingzhou.modules.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DagEdge {

    private String id;
    /** 兼容 X6：字符串 id，或 {cell, port} */
    private JsonNode source;
    private JsonNode target;

    public String sourceId() {
        return endpointId(source);
    }

    public String targetId() {
        return endpointId(target);
    }

    private static String endpointId(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.hasNonNull("cell")) {
            return node.get("cell").asText();
        }
        if (node.hasNonNull("id")) {
            return node.get("id").asText();
        }
        return null;
    }
}
