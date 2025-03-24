package org.example;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final AtomicInteger workerTasks = new AtomicInteger();
    private static final AtomicInteger callerTasks = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        // 1. Инициализация
        CustomCallerRunsPolicy policy = new CustomCallerRunsPolicy();
        policy.setLogEnabled(true); // Включить логирование для rejected tasks

        CustomThreadPoolConfig config = new CustomThreadPoolConfig.Builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .keepAliveTime(1, TimeUnit.SECONDS)
                .queueSize(3)
                .minSpareThreads(1)
                .rejectedExecutionHandler(policy)
                .loadBalancingStrategy(new RoundRobinStrategy())
                .build();

        CustomThreadPoolExecutor executor = new CustomThreadPoolExecutor(config);
        executor.setLogEnabled(true); // Включить общее логирование
        policy.setExecutor(executor);

        // 2. Запуск задач
        int totalTasks = 20;
        for (int i = 0; i < totalTasks; i++) {
            submitTask(executor, i);
            Thread.sleep(50); // Задержка между задачами
        }

        // 3. Завершение работы
        shutdownAndPrintStats(executor, policy, totalTasks);
    }

    private static void submitTask(CustomThreadPoolExecutor executor, int taskId) {
        try {
            executor.execute(() -> {
                trackThreadUsage();
                System.out.printf("[Task-%02d] Started in %s%n",
                        taskId, Thread.currentThread().getName());
                try {
                    Thread.sleep(300 + (int)(Math.random() * 700));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.printf("[Task-%02d] Completed%n", taskId);
            });
        } catch (Exception e) {
            System.err.printf("[Task-%02d] Submission failed: %s%n",
                    taskId, e.getMessage());
        }
    }

    private static void trackThreadUsage() {
        if (Thread.currentThread().getName().equals("main")) {
            callerTasks.incrementAndGet();
        } else {
            workerTasks.incrementAndGet();
        }
    }

    private static void shutdownAndPrintStats(CustomThreadPoolExecutor executor,
                                              CustomCallerRunsPolicy policy,
                                              int totalTasks) throws InterruptedException {
        System.out.println("\n=== Initiating shutdown ===");
        executor.shutdown();

        // Ожидание завершения с прогресс-баром
        while (executor.getActiveCount() > 0) {
            System.out.print(".");
            Thread.sleep(200);
        }

        printStatistics(executor, policy, totalTasks);
    }

    private static void printStatistics(CustomThreadPoolExecutor executor,
                                        CustomCallerRunsPolicy policy,
                                        int totalTasks) {
        System.out.println("\n\n=== Execution Statistics ===");
        System.out.printf("Total tasks submitted: %d%n", totalTasks);
        System.out.printf("Executed in workers: %d (%.1f%%)%n",
                workerTasks.get(), workerTasks.get() * 100.0 / totalTasks);
        System.out.printf("Executed in caller: %d (%.1f%%)%n",
                callerTasks.get(), callerTasks.get() * 100.0 / totalTasks);
        System.out.printf("Rejected tasks: %d (%.1f%%)%n",
                policy.getRejectedCount(), policy.getRejectedCount() * 100.0 / totalTasks);
        System.out.println("=== Shutdown complete ===");
    }
}