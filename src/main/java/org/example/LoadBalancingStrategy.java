package org.example;

import java.util.concurrent.BlockingQueue;

public interface LoadBalancingStrategy {
    int selectQueue(BlockingQueue<Runnable>[] queues, Runnable task);
}