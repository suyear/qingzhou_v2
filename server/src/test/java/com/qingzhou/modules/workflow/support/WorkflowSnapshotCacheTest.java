package com.qingzhou.modules.workflow.support;

import com.qingzhou.modules.workflow.entity.WorkflowSnapshot;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WorkflowSnapshotCacheTest {

    @Test
    void loadsImmutableSnapshotOnce() {
        WorkflowSnapshotCache cache = new WorkflowSnapshotCache();
        WorkflowSnapshot snapshot = new WorkflowSnapshot();
        snapshot.setId(3L);
        snapshot.setGraphJson("{\"nodes\":[]}");
        AtomicInteger loads = new AtomicInteger();
        WorkflowSnapshot first = cache.getById(3L, id -> {
            loads.incrementAndGet();
            return snapshot;
        });
        WorkflowSnapshot second = cache.getById(3L, id -> {
            loads.incrementAndGet();
            return snapshot;
        });
        assertSame(snapshot, first);
        assertSame(first, second);
        assertEquals(1, loads.get());
    }
}
