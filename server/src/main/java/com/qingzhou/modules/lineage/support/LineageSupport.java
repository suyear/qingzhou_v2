package com.qingzhou.modules.lineage.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LineageSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LineageSupport() {
    }

    public static List<ComponentRef> extractComponents(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(graphJson);
            JsonNode nodes = root.path("nodes");
            if (!nodes.isArray()) {
                return List.of();
            }
            Set<String> seen = new LinkedHashSet<>();
            List<ComponentRef> refs = new ArrayList<>();
            for (JsonNode node : nodes) {
                ComponentRef ref = fromNode(node);
                if (ref.isEmpty()) {
                    continue;
                }
                String key = ref.key();
                if (seen.add(key)) {
                    refs.add(ref);
                }
            }
            return refs;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static boolean usesComponent(String graphJson, Long componentId, String componentCode) {
        for (ComponentRef ref : extractComponents(graphJson)) {
            if (componentId != null && componentId.equals(ref.id())) {
                return true;
            }
            if (componentCode != null && !componentCode.isBlank() && componentCode.equals(ref.code())) {
                return true;
            }
        }
        return false;
    }

    public static String firstFailedNodeId(Iterable<? extends NodeStatus> logs) {
        if (logs == null) {
            return null;
        }
        for (NodeStatus log : logs) {
            if (log != null && isProblem(log.status())) {
                return log.nodeId();
            }
        }
        return null;
    }

    public static boolean isProblem(String status) {
        return "FAILED".equals(status) || "TIMEOUT".equals(status);
    }

    private static ComponentRef fromNode(JsonNode node) {
        Long id = longValue(firstNonNull(node.get("componentId"), node.path("data").get("componentId")));
        String code = textValue(firstNonNull(node.get("componentCode"), node.path("data").get("componentCode")));
        String name = textValue(firstNonNull(node.get("name"), node.path("data").get("componentName"), node.path("data").get("name")));
        return new ComponentRef(id, code, name);
    }

    private static JsonNode firstNonNull(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private static Long longValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        try {
            String text = node.asText();
            if (text == null || text.isBlank()) {
                return null;
            }
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String textValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }

    public record ComponentRef(Long id, String code, String name) {
        public boolean isEmpty() {
            return id == null && (code == null || code.isBlank());
        }

        public String key() {
            if (code != null && !code.isBlank()) {
                return "code:" + code;
            }
            return "id:" + id;
        }
    }

    public record NodeStatus(String nodeId, String status) {
    }
}
