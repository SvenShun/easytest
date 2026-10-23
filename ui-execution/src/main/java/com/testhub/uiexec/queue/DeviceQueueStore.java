package com.testhub.uiexec.queue;

import java.util.List;

/** Per-device FIFO queue port. Default implementation is in-memory; Redis is opt-in. */
public interface DeviceQueueStore {

    /** Appends {@code taskId} to the tail of the device queue (idempotent). */
    void enqueue(String deviceId, String taskId);

    /** Removes a task from the device queue; returns whether it was present. */
    boolean remove(String deviceId, String taskId);

    /** 1-based position of the task in the queue, or 0 when not queued. */
    int position(String deviceId, String taskId);

    /** Ordered queue contents (head first). */
    List<String> list(String deviceId);

    /** Removes and returns the head of the queue, or {@code null} when empty. */
    String popNext(String deviceId);
}
