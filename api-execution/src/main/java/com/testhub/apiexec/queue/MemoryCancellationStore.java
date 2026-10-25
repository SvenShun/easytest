package com.testhub.apiexec.queue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-process cancellation flags used in memory queue mode. */
public class MemoryCancellationStore implements CancellationStore {

    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    @Override
    public void requestCancel(String taskId) {
        if (taskId != null) {
            cancelled.add(taskId);
        }
    }

    @Override
    public boolean isCancelled(String taskId) {
        return taskId != null && cancelled.contains(taskId);
    }

    @Override
    public void clear(String taskId) {
        if (taskId != null) {
            cancelled.remove(taskId);
        }
    }
}
