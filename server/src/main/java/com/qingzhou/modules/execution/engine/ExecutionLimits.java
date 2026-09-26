package com.qingzhou.modules.execution.engine;

import java.util.concurrent.TimeUnit;

/**
 * 执行资源上限。节点 / 工作流超时在运行时收口，避免配置成极大值占满线程与连接。
 */
public final class ExecutionLimits {

    public static final int DEFAULT_NODE_TIMEOUT_MS = 10_000;
    public static final int MAX_NODE_TIMEOUT_MS = 300_000;
    public static final int DEFAULT_WORKFLOW_TIMEOUT_MS = 60_000;
    /** 单次执行硬顶 30 分钟，超过配置值也按此截断 */
    public static final int MAX_WORKFLOW_TIMEOUT_MS = 30 * 60 * 1000;
    public static final int MAX_RETRY_TIMES = 5;
    public static final int DEFAULT_MAX_PARALLEL = 8;

    private ExecutionLimits() {
    }

    public static int nodeTimeout(Integer configured) {
        int value = configured == null || configured < 1 ? DEFAULT_NODE_TIMEOUT_MS : configured;
        return Math.min(value, MAX_NODE_TIMEOUT_MS);
    }

    public static int workflowTimeout(Integer configured) {
        int value = configured == null || configured < 1 ? DEFAULT_WORKFLOW_TIMEOUT_MS : configured;
        return Math.min(value, MAX_WORKFLOW_TIMEOUT_MS);
    }

    public static int retryTimes(Integer configured) {
        int value = configured == null || configured < 0 ? 0 : configured;
        return Math.min(value, MAX_RETRY_TIMES);
    }

    /**
     * @return 本次调用允许的超时；0 表示工作流截止时间已到，调用方应直接记超时
     */
    public static int callTimeout(int nodeTimeoutMs, long deadlineNanos) {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        if (remaining < 1) {
            return 0;
        }
        int bounded = Math.min(Math.max(nodeTimeoutMs, 1), MAX_NODE_TIMEOUT_MS);
        return (int) Math.min(bounded, remaining);
    }

    public static boolean deadlineReached(long deadlineNanos) {
        return System.nanoTime() >= deadlineNanos;
    }

    public static void sleep(int intervalMs, long deadlineNanos) throws InterruptedException {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        if (remaining <= 0) {
            throw new InterruptedException("deadline");
        }
        Thread.sleep(Math.min(Math.max(0, intervalMs), remaining));
    }
}
