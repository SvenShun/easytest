package com.testhub.apiexec.queue;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/** Redis-backed cancellation flags with a one-hour TTL. */
public class RedisCancellationStore implements CancellationStore {

    static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate template;
    private final String cancelPrefix;

    public RedisCancellationStore(StringRedisTemplate template, String keyPrefix) {
        this.template = template;
        this.cancelPrefix = RedisKeyPrefix.normalize(keyPrefix) + "cancel:";
    }

    @Override
    public void requestCancel(String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            template.opsForValue().set(cancelKey(taskId), "1", TTL);
        }
    }

    @Override
    public boolean isCancelled(String taskId) {
        return taskId != null && Boolean.TRUE.equals(template.hasKey(cancelKey(taskId)));
    }

    @Override
    public void clear(String taskId) {
        if (taskId != null) {
            template.delete(cancelKey(taskId));
        }
    }

    String cancelKey(String taskId) {
        return cancelPrefix + taskId;
    }
}
