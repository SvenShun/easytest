package com.testhub.agent.dispatch;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.testhub.agent.executor.ScriptExecutor;
import com.testhub.agent.runtime.RuntimeBridge;

import jakarta.annotation.PreDestroy;

/**
 * Accepts dispatched tasks, runs them asynchronously on a worker pool and
 * implements cancellation. Every task produces exactly one terminal result.
 */
@Component
public class TaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatcher.class);

    private final TaskRegistry registry;
    private final ScriptExecutor scriptExecutor;
    private final RuntimeBridge runtimeBridge;
    private final TaskReporter taskReporter;
    private final ExecutorService taskExecutor;
    private final ExecutorService cancelExecutor;

    public TaskDispatcher(TaskRegistry registry, ScriptExecutor scriptExecutor,
            RuntimeBridge runtimeBridge, TaskReporter taskReporter) {
        this.registry = registry;
        this.scriptExecutor = scriptExecutor;
        this.runtimeBridge = runtimeBridge;
        this.taskReporter = taskReporter;
        this.taskExecutor = Executors.newCachedThreadPool(daemonFactory("agent-task-"));
        this.cancelExecutor = Executors.newCachedThreadPool(daemonFactory("agent-cancel-"));
    }

    /**
     * Register and start a task.
     *
     * @return the task, or {@code null} when the taskId is already known
     */
    public AgentTask dispatch(TaskDispatchRequest request) {
        AgentTask task = registry.create(request.taskId(), request.executorType());
        if (task == null) {
            return null;
        }
        taskExecutor.execute(() -> {
            try {
                if (request.isScript()) {
                    scriptExecutor.execute(task, request);
                } else {
                    runtimeBridge.execute(task, request);
                }
            } catch (Throwable t) {
                log.error("task {} crashed in executor", request.taskId(), t);
                if (!task.isResultReported()) {
                    String reason = request.isScript() ? "SCRIPT_FAILED" : "EXECUTOR_UNAVAILABLE";
                    taskReporter.report(task, request, TaskStatus.FAILED, reason,
                            "agent internal error: " + t.getClass().getSimpleName()
                                    + (t.getMessage() == null ? "" : ": " + t.getMessage()),
                            List.of());
                }
            } finally {
                task.setCancelHandler(null);
            }
        });
        return task;
    }

    /** Request cancellation; the worker/cancel handler reports the terminal state. */
    public void cancel(String taskId) {
        AgentTask task = registry.get(taskId);
        if (task == null) {
            log.info("cancel requested for unknown task {} (ignored)", taskId);
            return;
        }
        task.requestCancel();
        Process process = task.getProcess();
        if (process != null) {
            log.info("destroying process tree for cancelled task {}", taskId);
            AgentTask.destroyProcessTree(process);
        }
        Runnable handler = task.getCancelHandler();
        if (handler != null) {
            try {
                cancelExecutor.execute(handler);
            } catch (RejectedExecutionException e) {
                log.warn("cancel handler rejected for task {}; running inline", taskId);
                handler.run();
            }
        } else if (!task.isResultReported()) {
            log.info("task {} marked cancelled; executor will report the terminal state", taskId);
        }
    }

    public TaskRegistry registry() {
        return registry;
    }

    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdownNow();
        cancelExecutor.shutdownNow();
        try {
            taskExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
