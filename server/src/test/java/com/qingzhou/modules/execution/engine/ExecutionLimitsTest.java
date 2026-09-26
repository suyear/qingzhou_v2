package com.qingzhou.modules.execution.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionLimitsTest {

    @Test
    void workflowTimeoutUsesDefaultAndHardCap() {
        assertEquals(ExecutionLimits.DEFAULT_WORKFLOW_TIMEOUT_MS, ExecutionLimits.workflowTimeout(null));
        assertEquals(ExecutionLimits.DEFAULT_WORKFLOW_TIMEOUT_MS, ExecutionLimits.workflowTimeout(0));
        assertEquals(15_000, ExecutionLimits.workflowTimeout(15_000));
        assertEquals(ExecutionLimits.MAX_WORKFLOW_TIMEOUT_MS, ExecutionLimits.workflowTimeout(Integer.MAX_VALUE));
    }

    @Test
    void nodeTimeoutAndRetriesAreCapped() {
        assertEquals(ExecutionLimits.DEFAULT_NODE_TIMEOUT_MS, ExecutionLimits.nodeTimeout(null));
        assertEquals(ExecutionLimits.MAX_NODE_TIMEOUT_MS, ExecutionLimits.nodeTimeout(Integer.MAX_VALUE));
        assertEquals(0, ExecutionLimits.retryTimes(null));
        assertEquals(ExecutionLimits.MAX_RETRY_TIMES, ExecutionLimits.retryTimes(100));
    }

    @Test
    void callTimeoutRespectsDeadline() {
        assertEquals(0, ExecutionLimits.callTimeout(5_000, System.nanoTime() - 1_000_000L));
        int remaining = ExecutionLimits.callTimeout(30_000, System.nanoTime() + 2_000_000_000L);
        assertTrue(remaining > 0 && remaining <= 30_000);
    }
}
