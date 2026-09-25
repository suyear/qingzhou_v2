package com.qingzhou.modules.workflow.support;

import com.fasterxml.jackson.databind.node.TextNode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.workflow.dto.DagEdge;
import com.qingzhou.modules.workflow.dto.DagGraph;
import com.qingzhou.modules.workflow.dto.DagNode;
import com.qingzhou.modules.workflow.dto.ParamMappingItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagValidatorTest {

    @Test
    void linearDagIsValid() {
        DagGraph graph = graph(
                nodes("a", "b", "c"),
                edges("a", "b", "b", "c"));
        assertDoesNotThrow(() -> DagValidator.validate(graph, List.of()));
    }

    @Test
    void diamondDagIsValid() {
        DagGraph graph = graph(
                nodes("a", "b", "c", "d"),
                edges("a", "b", "a", "c", "b", "d", "c", "d"));
        assertDoesNotThrow(() -> DagValidator.validate(graph, List.of()));
    }

    @Test
    void cycleIsRejected() {
        DagGraph graph = graph(
                nodes("a", "b", "c"),
                edges("a", "b", "b", "c", "c", "a"));
        BizException ex = assertThrows(BizException.class, () -> DagValidator.validate(graph, List.of()));
        assertTrue(ex.getMessage().contains("闭环"));
    }

    @Test
    void selfLoopIsRejected() {
        DagGraph graph = graph(nodes("a"), edges("a", "a"));
        assertThrows(BizException.class, () -> DagValidator.validate(graph, List.of()));
    }

    @Test
    void missingEdgeEndpointIsRejected() {
        DagGraph graph = graph(nodes("a"), edges("a", "ghost"));
        BizException ex = assertThrows(BizException.class, () -> DagValidator.validate(graph, List.of()));
        assertTrue(ex.getMessage().contains("不存在的节点"));
    }

    @Test
    void mappingMustPointToExistingNode() {
        DagGraph graph = graph(nodes("a", "b"), edges("a", "b"));
        ParamMappingItem mapping = new ParamMappingItem();
        mapping.setFromNode("a");
        mapping.setFromPath("$.chatid");
        mapping.setToNode("missing");
        mapping.setToPath("$.chatid");
        assertThrows(BizException.class, () -> DagValidator.validate(graph, List.of(mapping)));
    }

    private static List<DagNode> nodes(String... ids) {
        return java.util.Arrays.stream(ids).map(id -> {
            DagNode node = new DagNode();
            node.setId(id);
            return node;
        }).toList();
    }

    private static List<DagEdge> edges(String... pairs) {
        List<DagEdge> list = new java.util.ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            DagEdge edge = new DagEdge();
            edge.setSource(TextNode.valueOf(pairs[i]));
            edge.setTarget(TextNode.valueOf(pairs[i + 1]));
            list.add(edge);
        }
        return list;
    }

    private static DagGraph graph(List<DagNode> nodes, List<DagEdge> edges) {
        DagGraph graph = new DagGraph();
        graph.setNodes(nodes);
        graph.setEdges(edges);
        return graph;
    }
}
