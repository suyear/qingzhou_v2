package com.qingzhou.modules.workflow.support;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.workflow.dto.DagEdge;
import com.qingzhou.modules.workflow.dto.DagGraph;
import com.qingzhou.modules.workflow.dto.DagNode;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 按入度分层：同一层互不依赖，可并行；层与层之间串行。
 */
public final class DagScheduler {

    private DagScheduler() {
    }

    public static List<List<String>> levels(DagGraph graph) {
        List<DagNode> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        if (nodes.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "工作流没有可执行节点");
        }
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (DagNode node : nodes) {
            outgoing.put(node.getId(), new ArrayList<>());
            indegree.put(node.getId(), 0);
        }
        List<DagEdge> edges = graph.getEdges() == null ? List.of() : graph.getEdges();
        for (DagEdge edge : edges) {
            String source = edge.sourceId();
            String target = edge.targetId();
            if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
                continue;
            }
            outgoing.get(source).add(target);
            indegree.merge(target, 1, Integer::sum);
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<List<String>> levels = new ArrayList<>();
        int visited = 0;
        while (!queue.isEmpty()) {
            int size = queue.size();
            List<String> level = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                String nodeId = queue.poll();
                level.add(nodeId);
                visited++;
                for (String next : outgoing.getOrDefault(nodeId, List.of())) {
                    int left = indegree.merge(next, -1, Integer::sum);
                    if (left == 0) {
                        queue.add(next);
                    }
                }
            }
            levels.add(level);
        }
        if (visited != nodes.size()) {
            throw new BizException(ResultCode.BAD_REQUEST, "工作流存在闭环，无法执行");
        }
        return levels;
    }
}
