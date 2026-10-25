package com.testhub.apiexec.config;

import com.testhub.apiexec.queue.CancellationStore;
import com.testhub.apiexec.queue.MemoryCancellationStore;
import com.testhub.apiexec.queue.MemoryTaskQueue;
import com.testhub.apiexec.queue.RedisCancellationStore;
import com.testhub.apiexec.queue.RedisTaskQueue;
import com.testhub.apiexec.queue.TaskQueue;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Locale;

/**
 * Selects the task queue backend: in-process memory (default) or the shared
 * Redis queue enabled by {@code testhub.apiexec.queue=redis}.
 */
@Configuration
public class TaskQueueConfig {

    @Bean
    public TaskQueue taskQueue(ApiExecProperties properties,
                               ObjectProvider<StringRedisTemplate> redisTemplates) {
        String mode = mode(properties);
        if ("redis".equals(mode)) {
            return new RedisTaskQueue(requireRedis(redisTemplates), properties.getRedisKeyPrefix());
        }
        return new MemoryTaskQueue();
    }

    @Bean
    public CancellationStore cancellationStore(ApiExecProperties properties,
                                               ObjectProvider<StringRedisTemplate> redisTemplates) {
        String mode = mode(properties);
        if ("redis".equals(mode)) {
            return new RedisCancellationStore(requireRedis(redisTemplates), properties.getRedisKeyPrefix());
        }
        return new MemoryCancellationStore();
    }

    private String mode(ApiExecProperties properties) {
        String mode = properties.getQueue() == null
                ? "memory" : properties.getQueue().trim().toLowerCase(Locale.ROOT);
        if (!mode.equals("memory") && !mode.equals("redis")) {
            throw new IllegalStateException("testhub.apiexec.queue 仅支持 memory 或 redis，当前值: "
                    + properties.getQueue());
        }
        return mode;
    }

    private StringRedisTemplate requireRedis(ObjectProvider<StringRedisTemplate> redisTemplates) {
        StringRedisTemplate template = redisTemplates.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("testhub.apiexec.queue=redis 但未配置 Spring Data Redis");
        }
        return template;
    }
}
