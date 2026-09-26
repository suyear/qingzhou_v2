package com.qingzhou.modules.workflow.support;

import com.qingzhou.modules.workflow.entity.WorkflowSnapshot;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 已发布快照按 id 缓存。快照行发布后不可变，因此无需在发布时主动失效；
 * 新版本是新 id。只缓存完整行，避免每次执行重复读取大段 graph_json。
 */
@Component
public class WorkflowSnapshotCache {

    static final int MAX_ENTRIES = 256;

    private final ConcurrentHashMap<Long, WorkflowSnapshot> byId = new ConcurrentHashMap<>();

    public WorkflowSnapshot getById(Long id, Function<Long, WorkflowSnapshot> loader) {
        if (id == null) {
            return null;
        }
        WorkflowSnapshot cached = byId.get(id);
        if (cached != null) {
            return cached;
        }
        WorkflowSnapshot loaded = loader.apply(id);
        if (loaded == null || loaded.getId() == null) {
            return loaded;
        }
        if (byId.size() >= MAX_ENTRIES) {
            byId.clear();
        }
        byId.put(loaded.getId(), loaded);
        return loaded;
    }

    public int size() {
        return byId.size();
    }
}
