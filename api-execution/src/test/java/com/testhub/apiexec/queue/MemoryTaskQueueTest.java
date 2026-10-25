package com.testhub.apiexec.queue;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Hermetic tests for the default memory queue used by every other test. */
class MemoryTaskQueueTest {

    private final MemoryTaskQueue queue = new MemoryTaskQueue();

    @Test
    void enqueueIsFifoAndDeduplicatedWhileQueued() throws Exception {
        queue.enqueue("task-a");
        queue.enqueue("task-b");
        queue.enqueue("task-a");

        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.poll(Duration.ZERO)).isEqualTo("task-a");
        assertThat(queue.poll(Duration.ZERO)).isEqualTo("task-b");
        assertThat(queue.poll(Duration.ZERO)).isNull();
        assertThat(queue.size()).isZero();

        // After being polled the id can be enqueued again.
        queue.enqueue("task-a");
        assertThat(queue.poll(Duration.ZERO)).isEqualTo("task-a");
    }

    @Test
    void removeDropsQueuedTaskAndAllowsReenqueue() throws Exception {
        queue.enqueue("task-a");
        queue.enqueue("task-b");

        assertThat(queue.remove("task-a")).isTrue();
        assertThat(queue.remove("task-a")).isFalse();
        assertThat(queue.size()).isEqualTo(1);

        queue.enqueue("task-a");
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.poll(Duration.ZERO)).isEqualTo("task-b");
        assertThat(queue.poll(Duration.ZERO)).isEqualTo("task-a");
    }

    @Test
    void pollBlocksUntilTimeoutThenReturnsNull() {
        long startedAt = System.nanoTime();
        String taskId = null;
        try {
            taskId = queue.poll(Duration.ofMillis(120));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(taskId).isNull();
        assertThat(elapsedMs).isGreaterThanOrEqualTo(100L);
    }
}
