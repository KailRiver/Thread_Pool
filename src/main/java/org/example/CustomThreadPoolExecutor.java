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
    private final AtomicInteger totalTasks = new AtomicInteger(0);
    private volatile boolean logEnabled = true;

    public CustomThreadPoolExecutor(CustomThreadPoolConfig config) {
        this.config = config;
        this.taskQueues = new BlockingQueue[config.getMaxPoolSize()];
        initializeQueues();
        initializeCoreThreads();
        log("[Pool] Initialized: core=%d, max=%d, queue=%d, keepAlive=%d%s",
                config.getCorePoolSize(), config.getMaxPoolSize(),
                config.getQueueSize(), config.getKeepAliveTime(),
                config.getTimeUnit().toString().toLowerCase().charAt(0));
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

    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            throw new IllegalStateException("ThreadPool is shutting down");
        }

        int taskId = totalTasks.incrementAndGet();
        log("[Pool] Task #%d submitted. Active: %d/%d, Queued: %d",
                taskId, getActiveCount(), threadCount.get(), getQueueSize());

        int queueIndex = config.getLoadBalancingStrategy().selectQueue(taskQueues, command);
        if (!taskQueues[queueIndex].offer(command)) {
            log("[Pool] Queue #%d full (size=%d), applying rejection policy",
                    queueIndex, taskQueues[queueIndex].size());
            config.getRejectedExecutionHandler().rejectedExecution(command, null);
            return;
        }

        if (shouldAddWorker()) {
            addWorker();
        }
    }

    private boolean shouldAddWorker() {
        return threadCount.get() < config.getMaxPoolSize() &&
                threadCount.get() - getActiveCount() < config.getMinSpareThreads();
    }

    private void addWorker() {
        if (threadCount.get() >= config.getMaxPoolSize()) {
            log("[Pool] Max threads reached (%d)", config.getMaxPoolSize());
            return;
        }
        Worker worker = new Worker();
        Thread thread = config.getThreadFactory().newThread(worker);
        if (workers.add(worker)) {
            threadCount.incrementAndGet();
            thread.start();
            log("[Pool] Worker #%d started: %s", threadCount.get(), thread.getName());
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
        log("[Pool] Initiating graceful shutdown...");
        isShutdown = true;
        for (Worker worker : workers) {
            worker.interruptIfIdle();
        }
    }

    @Override
    public void shutdownNow() {
        log("[Pool] Forcing immediate shutdown!");
        isShutdown = true;
        for (Worker worker : workers) {
            worker.interruptNow();
        }
        clearQueues();
    }

    private void clearQueues() {
        for (BlockingQueue<Runnable> queue : taskQueues) {
            queue.clear();
        }
        log("[Pool] All queues cleared");
    }

    public int getActiveCount() {
        int count = 0;
        for (Worker worker : workers) {
            if (worker.isRunning()) {
                count++;
            }
        }
        return count;
    }

    public int getTotalTasks() {
        return totalTasks.get();
    }

    public int getQueueSize() {
        int total = 0;
        for (BlockingQueue<Runnable> queue : taskQueues) {
            total += queue.size();
        }
        return total;
    }

    public boolean isShutdown() {
        return isShutdown;
    }

    public void setLogEnabled(boolean enabled) {
        this.logEnabled = enabled;
    }

    private void log(String format, Object... args) {
        if (logEnabled) {
            System.out.printf(format + "%n", args);
        }
    }

    private class Worker implements Runnable {
        private volatile boolean running = false;
        private volatile Thread currentThread;
        private volatile int tasksCompleted = 0;

        @Override
        public void run() {
            currentThread = Thread.currentThread();
            log("[Worker] %s started", currentThread.getName());
            try {
                while (!isShutdown) {
                    running = true;
                    Runnable task = getTask();
                    if (task != null) {
                        executeTask(task);
                    } else if (threadCount.get() > config.getCorePoolSize()) {
                        log("[Worker] %s idle timeout", currentThread.getName());
                        break;
                    }
                }
            } finally {
                workers.remove(this);
                threadCount.decrementAndGet();
                running = false;
                log("[Worker] %s terminated (completed %d tasks)",
                        currentThread.getName(), tasksCompleted);
            }
        }

        private void executeTask(Runnable task) {
            try {
                log("[Worker] %s executing task", currentThread.getName());
                task.run();
                tasksCompleted++;
            } catch (Exception e) {
                System.err.printf("[Worker] %s task failed: %s%n",
                        currentThread.getName(), e.getMessage());
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
                log("[Worker] %s interrupted while waiting", currentThread.getName());
                return null;
            }
        }

        public void interruptIfIdle() {
            if (!running && currentThread != null) {
                log("[Worker] Interrupting idle %s", currentThread.getName());
                currentThread.interrupt();
            }
        }

        public void interruptNow() {
            if (currentThread != null) {
                log("[Worker] Forcibly interrupting %s", currentThread.getName());
                currentThread.interrupt();
            }
        }

        public boolean isRunning() {
            return running;
        }
    }
}