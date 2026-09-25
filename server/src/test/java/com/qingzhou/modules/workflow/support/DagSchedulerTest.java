package com.qingzhou.modules.workflow.support;

import com.fasterxml.jackson.databind.node.TextNode;
import com.qingzhou.modules.workflow.dto.DagEdge;
import com.qingzhou.modules.workflow.dto.DagGraph;
import com.qingzhou.modules.workflow.dto.DagNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DagSchedulerTest {

    @Test
    void parallelThenJoin() {
        DagGraph graph = new DagGraph();
        graph.setNodes(nodes("a", "b", "c", "d"));
        graph.setEdges(edges("a", "b", "a", "c", "b", "d", "c", "d"));
        List<List<String>> levels = DagScheduler.levels(graph);
        assertEquals(List.of("a"), levels.get(0));
        assertEquals(2, levels.get(1).size());
        assertEquals(List.of("d"), levels.get(2));
    }

    private static List<DagNode> nodes(String... ids) {
        return java.util.Arrays.stream(ids).map(id -> {
            DagNode node = new DagNode();
            node.setId(id);
            return node;
        }).toList();
    }

    private static List<DagEdge> edges(String... pairs) {
        java.util.ArrayList<DagEdge> list = new java.util.ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            DagEdge edge = new DagEdge();
            edge.setSource(TextNode.valueOf(pairs[i]));
            edge.setTarget(TextNode.valueOf(pairs[i + 1]));
            list.add(edge);
        }
        return list;
    }
}
