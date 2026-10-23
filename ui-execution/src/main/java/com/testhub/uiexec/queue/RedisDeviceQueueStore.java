package com.testhub.uiexec.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis-backed FIFO queue using a List per device plus an index Set of queue
 * keys. The index is rebuilt lazily from existing {@code testhub:uiexec:queue:*}
 * keys after a restart. Bean creation never touches Redis, so an unreachable
 * Redis can only fail queue operations — not application startup.
 */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "queue-store", havingValue = "redis")
public class RedisDeviceQueueStore implements DeviceQueueStore {

    private static final String KEY_PREFIX = "testhub:uiexec:queue:";
    private static final String INDEX_KEY = "testhub:uiexec:queues";

    private final StringRedisTemplate redis;
    private final AtomicBoolean indexRebuilt = new AtomicBoolean(false);

    public RedisDeviceQueueStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public synchronized void enqueue(String deviceId, String taskId) {
        if (isBlank(deviceId) || isBlank(taskId)) {
            return;
        }
        ensureIndex();
        String key = key(deviceId);
        List<String> current = redis.opsForList().range(key, 0, -1);
        if (current == null || !current.contains(taskId)) {
            redis.opsForList().rightPush(key, taskId);
        }
        redis.opsForSet().add(INDEX_KEY, deviceId);
    }

    @Override
    public synchronized boolean remove(String deviceId, String taskId) {
        if (isBlank(deviceId) || isBlank(taskId)) {
            return false;
        }
        ensureIndex();
        Long removed = redis.opsForList().remove(key(deviceId), 0, taskId);
        return removed != null && removed > 0;
    }

    @Override
    public synchronized int position(String deviceId, String taskId) {
        if (isBlank(deviceId) || isBlank(taskId)) {
            return 0;
        }
        ensureIndex();
        List<String> current = redis.opsForList().range(key(deviceId), 0, -1);
        int index = current == null ? -1 : current.indexOf(taskId);
        return index < 0 ? 0 : index + 1;
    }

    @Override
    public synchronized List<String> list(String deviceId) {
        if (isBlank(deviceId)) {
            return List.of();
        }
        ensureIndex();
        List<String> current = redis.opsForList().range(key(deviceId), 0, -1);
        return current == null ? List.of() : new ArrayList<>(current);
    }

    @Override
    public synchronized String popNext(String deviceId) {
        if (isBlank(deviceId)) {
            return null;
        }
        ensureIndex();
        return redis.opsForList().leftPop(key(deviceId));
    }

    /** Rebuilds the device index from surviving queue keys after a restart. */
    private void ensureIndex() {
        if (indexRebuilt.get()) {
            return;
        }
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                String deviceId = key.substring(KEY_PREFIX.length());
                if (!deviceId.isBlank()) {
                    redis.opsForSet().add(INDEX_KEY, deviceId);
                }
            }
        }
        indexRebuilt.set(true);
    }

    private String key(String deviceId) {
        return KEY_PREFIX + deviceId;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
