package com.qingzhou.modules.workflow.support;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.workflow.dto.DagEdge;
import com.qingzhou.modules.workflow.dto.DagGraph;
import com.qingzhou.modules.workflow.dto.DagNode;
import com.qingzhou.modules.workflow.dto.ParamMappingItem;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAG 合法性：节点 ID 唯一、边端点存在、无闭环、参数映射指向已有节点。
 */
public final class DagValidator {

    private DagValidator() {
    }

    public static void validate(DagGraph graph, List<ParamMappingItem> mappings) {
        DagGraph safeGraph = graph == null ? new DagGraph() : graph;
        List<DagNode> nodes = safeGraph.getNodes() == null ? List.of() : safeGraph.getNodes();
        List<DagEdge> edges = safeGraph.getEdges() == null ? List.of() : safeGraph.getEdges();

        Set<String> nodeIds = new HashSet<>();
        for (DagNode node : nodes) {
            if (node == null || !StringUtils.hasText(node.getId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "DAG 节点缺少 id");
            }
            if (!nodeIds.add(node.getId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "DAG 节点 id 重复: " + node.getId());
            }
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        for (String id : nodeIds) {
            adjacency.put(id, new ArrayList<>());
        }
        for (DagEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            String source = edge.sourceId();
            String target = edge.targetId();
            if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
                throw new BizException(ResultCode.BAD_REQUEST, "DAG 边缺少 source/target");
            }
            if (!nodeIds.contains(source) || !nodeIds.contains(target)) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "DAG 边引用了不存在的节点: " + source + " -> " + target);
            }
            adjacency.get(source).add(target);
        }

        List<String> cycle = findCycle(adjacency);
        if (!cycle.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "工作流存在闭环: " + String.join(" -> ", cycle));
        }

        if (mappings == null) {
            return;
        }
        for (ParamMappingItem mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            if (!nodeIds.contains(mapping.getFromNode()) || !nodeIds.contains(mapping.getToNode())) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "参数映射引用了不存在的节点: " + mapping.getFromNode() + " -> " + mapping.getToNode());
            }
        }
    }

    /**
     * DFS 三色标记找环。返回一条环路（含回到起点），无环返回空列表。
     */
    static List<String> findCycle(Map<String, List<String>> adjacency) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Map<String, String> parent = new HashMap<>();
        for (String node : adjacency.keySet()) {
            if (visited.contains(node)) {
                continue;
            }
            List<String> cycle = dfs(node, adjacency, visiting, visited, parent);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private static List<String> dfs(
            String node,
            Map<String, List<String>> adjacency,
            Set<String> visiting,
            Set<String> visited,
            Map<String, String> parent) {
        visiting.add(node);
        for (String next : adjacency.getOrDefault(node, List.of())) {
            if (visiting.contains(next)) {
                return buildCycle(parent, node, next);
            }
            if (visited.contains(next)) {
                continue;
            }
            parent.put(next, node);
            List<String> cycle = dfs(next, adjacency, visiting, visited, parent);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        visiting.remove(node);
        visited.add(node);
        return List.of();
    }

    private static List<String> buildCycle(Map<String, String> parent, String last, String backTo) {
        List<String> path = new ArrayList<>();
        path.add(backTo);
        String cursor = last;
        path.add(cursor);
        while (cursor != null && !cursor.equals(backTo)) {
            cursor = parent.get(cursor);
            if (cursor == null) {
                break;
            }
            path.add(cursor);
        }
        Collections.reverse(path);
        return path;
    }
}
