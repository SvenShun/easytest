package com.testhub.uiexec.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Default in-memory FIFO queue. Queues are lost on restart (V1 limitation);
 * use {@code testhub.uiexec.queue-store=redis} for a durable queue.
 */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "queue-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryDeviceQueueStore implements DeviceQueueStore {

    private final Map<String, LinkedList<String>> queues = new LinkedHashMap<>();

    @Override
    public synchronized void enqueue(String deviceId, String taskId) {
        if (isBlank(deviceId) || isBlank(taskId)) {
            return;
        }
        LinkedList<String> queue = queues.computeIfAbsent(deviceId, key -> new LinkedList<>());
        if (!queue.contains(taskId)) {
            queue.addLast(taskId);
        }
    }

    @Override
    public synchronized boolean remove(String deviceId, String taskId) {
        LinkedList<String> queue = queues.get(deviceId);
        if (queue == null) {
            return false;
        }
        boolean removed = queue.remove(taskId);
        if (queue.isEmpty()) {
            queues.remove(deviceId);
        }
        return removed;
    }

    @Override
    public synchronized int position(String deviceId, String taskId) {
        LinkedList<String> queue = queues.get(deviceId);
        if (queue == null) {
            return 0;
        }
        int index = queue.indexOf(taskId);
        return index < 0 ? 0 : index + 1;
    }

    @Override
    public synchronized List<String> list(String deviceId) {
        LinkedList<String> queue = queues.get(deviceId);
        return queue == null ? List.of() : new ArrayList<>(queue);
    }

    @Override
    public synchronized String popNext(String deviceId) {
        LinkedList<String> queue = queues.get(deviceId);
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        String head = queue.removeFirst();
        if (queue.isEmpty()) {
            queues.remove(deviceId);
        }
        return head;
    }

    /** Exposed for diagnostics / tests. */
    public synchronized Map<String, List<String>> snapshot() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        queues.forEach((key, value) -> copy.put(key, Collections.unmodifiableList(new ArrayList<>(value))));
        return copy;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
