package com.testhub.apiexec;

import com.testhub.apiexec.domain.ApiTaskEntity;
import com.testhub.apiexec.domain.ApiTaskStatus;
import com.testhub.apiexec.queue.TaskQueue;
import com.testhub.apiexec.repository.ApiTaskRepository;
import com.testhub.apiexec.service.ApiExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Startup recovery: stale RUNNING tasks fail and PENDING tasks are re-enqueued
 * exactly once. Workers are disabled so the queue state is observable.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apiexec-recovery;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "testhub.apiexec.core-base-url=http://127.0.0.1:1",
        "testhub.apiexec.callback-enabled=false",
        "testhub.apiexec.workers=0",
        "testhub.apiexec.instance-id=recovery-test"
})
class ApiExecRecoveryTest {

    @Autowired
    private ApiTaskRepository tasks;

    @Autowired
    private TaskQueue taskQueue;

    @Autowired
    private ApiExecutionService executionService;

    @BeforeEach
    void resetState() throws InterruptedException {
        tasks.deleteAll();
        while (taskQueue.poll(Duration.ZERO) != null) {
            // drain leftovers from the startup runner
        }
    }

    @Test
    void marksRunningFailedAndReenqueuesPendingIdempotently() throws Exception {
        ApiTaskEntity running = task("task-running", ApiTaskStatus.RUNNING);
        running.setStartedAt(Instant.now().minusSeconds(30));
        running.setWorkerId("legacy-instance-1");
        tasks.save(running);
        assertThat(running.getStatus()).isEqualTo(ApiTaskStatus.RUNNING);

        ApiTaskEntity alreadyQueued = task("task-pending-queued", ApiTaskStatus.PENDING);
        tasks.save(alreadyQueued);
        taskQueue.enqueue(alreadyQueued.getId());

        ApiTaskEntity notQueued = task("task-pending-new", ApiTaskStatus.PENDING);
        tasks.save(notQueued);
        assertThat(taskQueue.size()).isEqualTo(1);

        int recovered = executionService.recoverInterrupted();

        assertThat(recovered).isEqualTo(1);
        ApiTaskEntity recoveredRunning = tasks.findById(running.getId()).orElseThrow();
        assertThat(recoveredRunning.getStatus()).isEqualTo(ApiTaskStatus.FAILED);
        assertThat(recoveredRunning.getFailureReason()).isEqualTo("INTERRUPTED");
        assertThat(recoveredRunning.getWorkerId()).isEqualTo("legacy-instance-1");
        assertThat(recoveredRunning.getReportJson()).isNotBlank();

        assertThat(tasks.findById(alreadyQueued.getId()).orElseThrow().getStatus())
                .isEqualTo(ApiTaskStatus.PENDING);
        assertThat(tasks.findById(notQueued.getId()).orElseThrow().getStatus())
                .isEqualTo(ApiTaskStatus.PENDING);
        assertThat(taskQueue.size()).isEqualTo(2);

        // Idempotent: a second recovery does not duplicate queued tasks.
        assertThat(executionService.recoverInterrupted()).isZero();
        assertThat(taskQueue.size()).isEqualTo(2);
        assertThat(taskQueue.poll(Duration.ZERO)).isEqualTo(alreadyQueued.getId());
        assertThat(taskQueue.poll(Duration.ZERO)).isEqualTo(notQueued.getId());
    }

    private ApiTaskEntity task(String id, ApiTaskStatus status) {
        ApiTaskEntity task = new ApiTaskEntity(id, "case-" + id, "GET",
                "http://127.0.0.1:1/health", status, 5, Instant.now());
        task.setPlanRunId("run-" + id);
        return task;
    }
}
