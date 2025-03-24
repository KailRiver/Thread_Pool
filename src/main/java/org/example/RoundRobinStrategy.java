package org.example;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinStrategy implements LoadBalancingStrategy {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public int selectQueue(BlockingQueue<Runnable>[] queues, Runnable task) {
        return counter.getAndIncrement() % queues.length;
    }
}