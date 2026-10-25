package com.testhub.apiexec.queue;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-process FIFO queue used by default and by tests. A concurrent set keeps
 * {@link #enqueue(String)} idempotent while a task id is waiting.
 */
public class MemoryTaskQueue implements TaskQueue {

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Set<String> queued = ConcurrentHashMap.newKeySet();

    @Override
    public void enqueue(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        if (!queued.add(taskId)) {
            return;
        }
        if (!queue.offer(taskId)) {
            queued.remove(taskId);
        }
    }

    @Override
    public String poll(Duration timeout) throws InterruptedException {
        Duration wait = timeout == null ? Duration.ZERO : timeout;
        String taskId;
        if (wait.isZero() || wait.isNegative()) {
            taskId = queue.poll();
        } else {
            taskId = queue.poll(wait.toMillis(), TimeUnit.MILLISECONDS);
        }
        if (taskId != null) {
            queued.remove(taskId);
        }
        return taskId;
    }

    @Override
    public boolean remove(String taskId) {
        if (taskId == null) {
            return false;
        }
        queued.remove(taskId);
        return queue.remove(taskId);
    }

    @Override
    public long size() {
        return queue.size();
    }
}
