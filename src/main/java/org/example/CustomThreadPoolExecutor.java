package org.example;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CustomThreadPoolExecutor implements CustomExecutor {
    private final AtomicInteger threadCount = new AtomicInteger(0);
    private final Set<Worker> workers = Collections.synchronizedSet(new HashSet<>());
    private final BlockingQueue<Runnable>[] taskQueues;
    private final CustomThreadPoolConfig config;
    private volatile boolean isShutdown = false;

    public CustomThreadPoolExecutor(CustomThreadPoolConfig config) {
        this.config = config;
        this.taskQueues = new BlockingQueue[config.getMaxPoolSize()];
        initializeQueues();
        initializeCoreThreads();
    }

    private void initializeQueues() {
        for (int i = 0; i < taskQueues.length; i++) {
            taskQueues[i] = new LinkedBlockingQueue<>(config.getQueueSize());
        }
    }

    private void initializeCoreThreads() {
        for (int i = 0; i < config.getCorePoolSize(); i++) {
            addWorker();
        }
    }

    private void addWorker() {
        if (threadCount.get() >= config.getMaxPoolSize()) {
            return;
        }
        Worker worker = new Worker();
        Thread thread = config.getThreadFactory().newThread(worker);
        workers.add(worker);
        threadCount.incrementAndGet();
        thread.start();
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            throw new IllegalStateException("ThreadPool is shutting down");
        }

        int queueIndex = config.getLoadBalancingStrategy().selectQueue(taskQueues, command);
        if (!taskQueues[queueIndex].offer(command)) {
            config.getRejectedExecutionHandler().rejectedExecution(command, null);
        }

        if (threadCount.get() < config.getMaxPoolSize() &&
                threadCount.get() - getActiveCount() < config.getMinSpareThreads()) {
            addWorker();
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> future = new FutureTask<>(callable);
        execute(future);
        return future;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        for (Worker worker : workers) {
            worker.interruptIfIdle();
        }
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        for (Worker worker : workers) {
            worker.interruptNow();
        }
    }

    private int getActiveCount() {
        int count = 0;
        for (Worker worker : workers) {
            if (worker.isRunning()) {
                count++;
            }
        }
        return count;
    }

    private class Worker implements Runnable {
        private volatile boolean running = false;
        private volatile Thread currentThread;

        @Override
        public void run() {
            currentThread = Thread.currentThread();
            try {
                while (!isShutdown) {
                    running = true;
                    Runnable task = getTask();
                    if (task != null) {
                        task.run();
                    } else if (threadCount.get() > config.getCorePoolSize()) {
                        break;
                    }
                }
            } finally {
                workers.remove(this);
                threadCount.decrementAndGet();
                running = false;
            }
        }

        private Runnable getTask() {
            try {
                for (BlockingQueue<Runnable> queue : taskQueues) {
                    Runnable task = queue.poll();
                    if (task != null) {
                        return task;
                    }
                }
                return taskQueues[0].poll(config.getKeepAliveTime(), config.getTimeUnit());
            } catch (InterruptedException e) {
                return null;
            }
        }

        public void interruptIfIdle() {
            if (!running && currentThread != null) {
                currentThread.interrupt();
            }
        }

        public void interruptNow() {
            if (currentThread != null) {
                currentThread.interrupt();
            }
        }

        public boolean isRunning() {
            return running;
        }
    }
}