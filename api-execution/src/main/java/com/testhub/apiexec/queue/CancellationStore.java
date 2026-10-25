package com.testhub.apiexec.queue;

/**
 * Cross-instance cancellation flag. The DB status is authoritative; this flag
 * lets a worker on another instance notice a cancel that arrived while a
 * request was in flight.
 */
public interface CancellationStore {

    void requestCancel(String taskId);

    boolean isCancelled(String taskId);

    void clear(String taskId);
}
