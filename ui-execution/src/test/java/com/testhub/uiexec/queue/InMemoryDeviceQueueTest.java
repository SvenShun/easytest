package com.testhub.uiexec.queue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDeviceQueueTest {

    private final InMemoryDeviceQueueStore store = new InMemoryDeviceQueueStore();

    @Test
    void enqueueIsFifo() {
        store.enqueue("d1", "t1");
        store.enqueue("d1", "t2");
        store.enqueue("d1", "t3");

        assertThat(store.list("d1")).containsExactly("t1", "t2", "t3");
        assertThat(store.popNext("d1")).isEqualTo("t1");
        assertThat(store.popNext("d1")).isEqualTo("t2");
        assertThat(store.popNext("d1")).isEqualTo("t3");
        assertThat(store.popNext("d1")).isNull();
    }

    @Test
    void enqueueIsIdempotent() {
        store.enqueue("d1", "t1");
        store.enqueue("d1", "t1");

        assertThat(store.list("d1")).containsExactly("t1");
    }

    @Test
    void positionIsOneBasedAndZeroWhenAbsent() {
        store.enqueue("d1", "t1");
        store.enqueue("d1", "t2");

        assertThat(store.position("d1", "t1")).isEqualTo(1);
        assertThat(store.position("d1", "t2")).isEqualTo(2);
        assertThat(store.position("d1", "t3")).isZero();
        assertThat(store.position("d2", "t1")).isZero();
    }

    @Test
    void removeDeletesTaskAndKeepsOrder() {
        store.enqueue("d1", "t1");
        store.enqueue("d1", "t2");
        store.enqueue("d1", "t3");

        assertThat(store.remove("d1", "t2")).isTrue();
        assertThat(store.remove("d1", "t2")).isFalse();
        assertThat(store.list("d1")).containsExactly("t1", "t3");
        assertThat(store.position("d1", "t3")).isEqualTo(2);
    }

    @Test
    void queuesAreIsolatedPerDevice() {
        store.enqueue("d1", "t1");
        store.enqueue("d2", "t2");

        assertThat(store.list("d1")).containsExactly("t1");
        assertThat(store.list("d2")).containsExactly("t2");
        assertThat(store.popNext("d2")).isEqualTo("t2");
        assertThat(store.popNext("d1")).isEqualTo("t1");
    }
}
