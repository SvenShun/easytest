package com.testhub.apiexec.queue;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional Redis coverage. The test skips itself when no local Redis is
 * available, so the Maven build never depends on an external service.
 */
class RedisTaskQueueTest {

    @Test
    void redisQueueIsFifoDeduplicatedAndRemovable() {
        withRedis((template, prefix) -> {
            RedisTaskQueue queue = new RedisTaskQueue(template, prefix);
            queue.enqueue("task-a");
            queue.enqueue("task-b");
            queue.enqueue("task-a");

            assertThat(queue.size()).isEqualTo(2);
            assertThat(queue.poll(Duration.ofSeconds(1))).isEqualTo("task-a");
            assertThat(queue.poll(Duration.ofSeconds(1))).isEqualTo("task-b");
            assertThat(queue.poll(Duration.ZERO)).isNull();
            assertThat(queue.size()).isZero();

            queue.enqueue("task-c");
            assertThat(queue.remove("task-c")).isTrue();
            assertThat(queue.remove("task-c")).isFalse();
            assertThat(queue.size()).isZero();
        });
    }

    @Test
    void redisCancellationFlagRoundTrips() {
        withRedis((template, prefix) -> {
            RedisCancellationStore store = new RedisCancellationStore(template, prefix);
            assertThat(store.isCancelled("task-a")).isFalse();
            store.requestCancel("task-a");
            assertThat(store.isCancelled("task-a")).isTrue();
            store.clear("task-a");
            assertThat(store.isCancelled("task-a")).isFalse();
        });
    }

    private void withRedis(RedisScenario scenario) {
        LettuceConnectionFactory factory = newFactory();
        String prefix = "testhub:test:apiexec:" + UUID.randomUUID().toString().replace("-", "") + ":";
        try {
            factory.afterPropertiesSet();
            Assumptions.assumeTrue(ping(factory), "Redis is not available on 127.0.0.1:6379; skipping");
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            try {
                scenario.run(template, prefix);
            } finally {
                template.delete(List.of(prefix + "queue", prefix + "queued", prefix + "cancel:task-a"));
            }
        } finally {
            factory.destroy();
        }
    }

    private LettuceConnectionFactory newFactory() {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(1))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(Duration.ofSeconds(1))
                                .build())
                        .build())
                .build();
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 6379),
                clientConfiguration);
    }

    private boolean ping(LettuceConnectionFactory factory) {
        RedisConnection connection = null;
        try {
            connection = factory.getConnection();
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (RuntimeException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @FunctionalInterface
    private interface RedisScenario {
        void run(StringRedisTemplate template, String prefix);
    }
}
