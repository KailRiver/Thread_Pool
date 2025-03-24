package org.example;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.atomic.AtomicInteger; // Добавлен этот импорт

public class CustomThreadPoolConfig {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;
    private RejectedExecutionHandler rejectedExecutionHandler;
    private final ThreadFactory threadFactory;
    private final LoadBalancingStrategy loadBalancingStrategy;

    private CustomThreadPoolConfig(Builder builder) {
        this.corePoolSize = builder.corePoolSize;
        this.maxPoolSize = builder.maxPoolSize;
        this.keepAliveTime = builder.keepAliveTime;
        this.timeUnit = builder.timeUnit;
        this.queueSize = builder.queueSize;
        this.minSpareThreads = builder.minSpareThreads;
        this.rejectedExecutionHandler = builder.rejectedExecutionHandler;
        this.threadFactory = builder.threadFactory;
        this.loadBalancingStrategy = builder.loadBalancingStrategy;
    }

    // Геттеры
    public int getCorePoolSize() { return corePoolSize; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public long getKeepAliveTime() { return keepAliveTime; }
    public TimeUnit getTimeUnit() { return timeUnit; }
    public int getQueueSize() { return queueSize; }
    public int getMinSpareThreads() { return minSpareThreads; }
    public RejectedExecutionHandler getRejectedExecutionHandler() { return rejectedExecutionHandler; }
    public ThreadFactory getThreadFactory() { return threadFactory; }
    public LoadBalancingStrategy getLoadBalancingStrategy() { return loadBalancingStrategy; }

    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        this.rejectedExecutionHandler = handler;
    }

    public static class Builder {
        private int corePoolSize = 1;
        private int maxPoolSize = Runtime.getRuntime().availableProcessors();
        private long keepAliveTime = 60;
        private TimeUnit timeUnit = TimeUnit.SECONDS;
        private int queueSize = 10;
        private int minSpareThreads = 0;
        private RejectedExecutionHandler rejectedExecutionHandler;
        private ThreadFactory threadFactory;
        private LoadBalancingStrategy loadBalancingStrategy;

        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        public Builder keepAliveTime(long keepAliveTime, TimeUnit timeUnit) {
            this.keepAliveTime = keepAliveTime;
            this.timeUnit = timeUnit;
            return this;
        }

        public Builder queueSize(int queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder minSpareThreads(int minSpareThreads) {
            this.minSpareThreads = minSpareThreads;
            return this;
        }

        public Builder rejectedExecutionHandler(RejectedExecutionHandler handler) {
            this.rejectedExecutionHandler = handler;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder loadBalancingStrategy(LoadBalancingStrategy strategy) {
            this.loadBalancingStrategy = strategy;
            return this;
        }

        public CustomThreadPoolConfig build() {
            if (threadFactory == null) {
                threadFactory = new DefaultThreadFactory();
            }
            if (loadBalancingStrategy == null) {
                loadBalancingStrategy = new RoundRobinStrategy();
            }
            if (rejectedExecutionHandler == null) {
                rejectedExecutionHandler = new CustomCallerRunsPolicy();
            }
            return new CustomThreadPoolConfig(this);
        }
    }

    static class DefaultThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "CustomPool-thread-" + threadNumber.getAndIncrement());
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}