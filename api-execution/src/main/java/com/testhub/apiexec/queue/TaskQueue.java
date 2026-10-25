package com.testhub.apiexec.queue;

import java.time.Duration;

/**
 * Shared task queue consumed by every api-execution instance. Implementations
 * are responsible for de-duplicating task ids while they are queued.
 */
public interface TaskQueue {

    void enqueue(String taskId);

    /**
     * Blocks up to {@code timeout} for the next task id.
     *
     * @return the oldest queued task id, or {@code null} on timeout
     */
    String poll(Duration timeout) throws InterruptedException;

    /** Removes a task id if it is still queued. */
    boolean remove(String taskId);

    /** Number of task ids currently waiting in the queue. */
    long size();
}
