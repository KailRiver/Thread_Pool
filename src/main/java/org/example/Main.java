package org.example;

import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        CustomThreadPoolConfig config = new CustomThreadPoolConfig.Builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .keepAliveTime(5, TimeUnit.SECONDS)
                .queueSize(5)
                .minSpareThreads(1)
                .rejectedExecutionHandler(new CustomCallerRunsPolicy())
                .loadBalancingStrategy(new RoundRobinStrategy())
                .build();

        CustomExecutor executor = new CustomThreadPoolExecutor(config);

        // Отправка задач
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            try {
                executor.submit(() -> {
                    System.out.printf("[Task-%d] Started in %s%n",
                            taskId, Thread.currentThread().getName());
                    Thread.sleep(1000 + (int)(Math.random() * 2000));
                    System.out.printf("[Task-%d] Completed%n", taskId);
                    return taskId;
                });
            } catch (Exception e) {
                System.out.printf("[Task-%d] Rejected: %s%n", taskId, e.getMessage());
            }
        }

        // Завершение работы
        executor.shutdown();
    }
}