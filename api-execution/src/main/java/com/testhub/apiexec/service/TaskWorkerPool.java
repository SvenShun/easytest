package com.testhub.apiexec.service;

import com.testhub.apiexec.config.ApiExecProperties;
import com.testhub.apiexec.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Starts {@code testhub.apiexec.workers} polling workers on this instance.
 * Every worker claims task ids from the shared {@link TaskQueue}; the queue is
 * FIFO and de-duplicates ids, so multiple instances safely share one backlog.
 *
 * <p>Starts after {@link StartupRecovery} ({@code @Order(0)}) has failed stale
 * RUNNING tasks and re-enqueued PENDING ones.</p>
 */
@Component
@Order(10)
public class TaskWorkerPool implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TaskWorkerPool.class);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    private static final long ERROR_BACKOFF_MS = 1_000L;

    private final ApiExecutionService executionService;
    private final TaskQueue taskQueue;
    private final ApiExecProperties properties;
    private final List<Thread> threads = new ArrayList<>();

    private volatile boolean running;

    public TaskWorkerPool(ApiExecutionService executionService, TaskQueue taskQueue,
                          ApiExecProperties properties) {
        this.executionService = executionService;
        this.taskQueue = taskQueue;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        start();
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        int count = Math.max(0, properties.getWorkers());
        String instanceId = properties.getInstanceId();
        for (int index = 1; index <= count; index++) {
            String workerId = instanceId + "-" + index;
            Thread thread = new Thread(() -> loop(workerId), "api-exec-worker-" + index);
            thread.setDaemon(true);
            thread.start();
            threads.add(thread);
        }
        if (count > 0) {
            log.info("api-execution 启动 {} 个 worker，instanceId={}", count, instanceId);
        } else {
            log.info("api-execution workers=0，本实例不消费任务（instanceId={}）", instanceId);
        }
    }

    private void loop(String workerId) {
        while (running) {
            String taskId;
            try {
                taskId = taskQueue.poll(POLL_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                if (!running) {
                    break;
                }
                log.warn("任务队列拉取失败 workerId={}: {}", workerId, e.getMessage());
                backoff();
                continue;
            }
            if (taskId == null) {
                continue;
            }
            try {
                executionService.execute(taskId, workerId);
            } catch (RuntimeException e) {
                log.error("worker 执行任务异常 taskId={} workerId={}", taskId, workerId, e);
                try {
                    executionService.failUnexpected(taskId, e);
                } catch (RuntimeException failure) {
                    log.error("worker 标记任务失败异常 taskId={} workerId={}", taskId, workerId, failure);
                }
            }
        }
    }

    private void backoff() {
        try {
            Thread.sleep(ERROR_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void destroy() {
        running = false;
        for (Thread thread : threads) {
            thread.interrupt();
        }
        for (Thread thread : threads) {
            try {
                thread.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (!threads.isEmpty()) {
            log.info("api-execution 停止 {} 个 worker", threads.size());
        }
        threads.clear();
    }
}
