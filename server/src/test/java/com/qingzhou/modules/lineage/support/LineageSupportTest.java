package com.qingzhou.modules.lineage.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineageSupportTest {

    @Test
    void extractComponentsFromGraphAndData() {
        String graph = """
                {"nodes":[
                  {"id":"n1","componentId":11,"componentCode":"order.query","name":"查订单"},
                  {"id":"n2","data":{"componentId":12,"componentCode":"stock.query","componentName":"查库存"}},
                  {"id":"n3","componentCode":"order.query"}
                ]}
                """;
        List<LineageSupport.ComponentRef> refs = LineageSupport.extractComponents(graph);
        assertEquals(2, refs.size());
        assertEquals("order.query", refs.get(0).code());
        assertEquals("stock.query", refs.get(1).code());
        assertTrue(LineageSupport.usesComponent(graph, 11L, null));
        assertTrue(LineageSupport.usesComponent(graph, null, "stock.query"));
        assertFalse(LineageSupport.usesComponent(graph, 99L, "missing"));
    }

    @Test
    void firstFailedNodeAndBlankGraph() {
        assertEquals(List.of(), LineageSupport.extractComponents(""));
        assertEquals(List.of(), LineageSupport.extractComponents("{not-json"));
        assertEquals("n2", LineageSupport.firstFailedNodeId(List.of(
                new LineageSupport.NodeStatus("n1", "SUCCESS"),
                new LineageSupport.NodeStatus("n2", "FAILED"),
                new LineageSupport.NodeStatus("n3", "TIMEOUT")
        )));
        assertTrue(LineageSupport.isProblem("TIMEOUT"));
        assertFalse(LineageSupport.isProblem("SUCCESS"));
    }
}
