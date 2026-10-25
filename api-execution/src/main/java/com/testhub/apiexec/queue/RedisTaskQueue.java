package com.testhub.apiexec.queue;

import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis list-backed shared queue: {@code LPUSH} + blocking {@code RPOP} gives
 * FIFO order across instances. A companion set records queued task ids so
 * {@link #enqueue(String)} is idempotent (used by startup recovery).
 */
public class RedisTaskQueue implements TaskQueue {

    private final StringRedisTemplate template;
    private final String queueKey;
    private final String queuedKey;

    public RedisTaskQueue(StringRedisTemplate template, String keyPrefix) {
        this.template = template;
        String prefix = RedisKeyPrefix.normalize(keyPrefix);
        this.queueKey = prefix + "queue";
        this.queuedKey = prefix + "queued";
    }

    @Override
    public void enqueue(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        Long added = template.opsForSet().add(queuedKey, taskId);
        if (added == null || added == 0L) {
            return;
        }
        try {
            template.opsForList().leftPush(queueKey, taskId);
        } catch (RuntimeException e) {
            template.opsForSet().remove(queuedKey, taskId);
            throw e;
        }
    }

    @Override
    public String poll(Duration timeout) {
        long millis = timeout == null || timeout.isNegative() ? 0L : timeout.toMillis();
        ListOperations<String, String> lists = template.opsForList();
        String taskId = millis <= 0L
                ? lists.rightPop(queueKey)
                : lists.rightPop(queueKey, millis, TimeUnit.MILLISECONDS);
        if (taskId != null) {
            template.opsForSet().remove(queuedKey, taskId);
        }
        return taskId;
    }

    @Override
    public boolean remove(String taskId) {
        if (taskId == null) {
            return false;
        }
        SetOperations<String, String> sets = template.opsForSet();
        sets.remove(queuedKey, taskId);
        Long removed = template.opsForList().remove(queueKey, 0L, taskId);
        return removed != null && removed > 0L;
    }

    @Override
    public long size() {
        Long size = template.opsForList().size(queueKey);
        return size == null ? 0L : size;
    }

    String queueKey() {
        return queueKey;
    }

    String queuedKey() {
        return queuedKey;
    }
}
