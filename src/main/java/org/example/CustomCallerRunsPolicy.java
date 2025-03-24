package org.example;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomCallerRunsPolicy implements RejectedExecutionHandler {
    private final AtomicInteger rejectedCount = new AtomicInteger(0);
    private CustomThreadPoolExecutor executor;
    private volatile boolean logEnabled = true;

    public CustomCallerRunsPolicy() {}

    public void setExecutor(CustomThreadPoolExecutor executor) {
        this.executor = executor;
    }

    public void setLogEnabled(boolean enabled) {
        this.logEnabled = enabled;
    }

    @Override
    public void rejectedExecution(Runnable r, java.util.concurrent.ThreadPoolExecutor unused) {
        if (executor == null) {
            throw new RejectedExecutionException("Executor not initialized in CustomCallerRunsPolicy");
        }

        int count = rejectedCount.incrementAndGet();
        String callerThread = Thread.currentThread().getName();

        log("[Rejected #%d] Executing in %s (Active: %d, Queued: %d)",
                count, callerThread,
                executor.getActiveCount(),
                executor.getQueueSize());

        if (executor.isShutdown()) {
            System.err.println("[Rejected] Pool shutting down, task rejected");
            throw new RejectedExecutionException("Executor has been shutdown");
        }

        long startTime = System.currentTimeMillis();
        try {
            r.run();
            log("[Rejected #%d] Completed in %s (%dms)",
                    count, callerThread,
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            System.err.printf("[Rejected #%d] Failed in %s: %s%n",
                    count, callerThread, e.getMessage());
            throw new RejectedExecutionException("Task execution failed", e);
        }
    }

    public int getRejectedCount() {
        return rejectedCount.get();
    }

    private void log(String format, Object... args) {
        if (logEnabled) {
            System.out.printf(format + "%n", args);
        }
    }
}