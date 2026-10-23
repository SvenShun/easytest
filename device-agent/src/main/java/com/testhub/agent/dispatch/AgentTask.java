package com.testhub.agent.dispatch;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/** In-memory record of one task accepted by this agent. */
public class AgentTask {

    private final String taskId;
    private final String executorType;
    private final Instant acceptedAt;

    private volatile String status;
    private volatile Instant finishedAt;
    private volatile boolean cancelRequested;
    private volatile Process process;
    private volatile Runnable cancelHandler;

    private final AtomicBoolean resultReported = new AtomicBoolean(false);

    public AgentTask(String taskId, String executorType) {
        this.taskId = taskId;
        this.executorType = executorType;
        this.acceptedAt = Instant.now();
        this.status = TaskStatus.RUNNING;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getExecutorType() {
        return executorType;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        if (TaskStatus.isTerminal(status)) {
            this.finishedAt = Instant.now();
        }
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void requestCancel() {
        this.cancelRequested = true;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public Process getProcess() {
        return process;
    }

    public void setProcess(Process process) {
        this.process = process;
        if (process != null && cancelRequested) {
            destroyProcessTree(process);
        }
    }

    /** Kill the process and any children spawned by the entry command. */
    public static void destroyProcessTree(Process process) {
        if (process == null) {
            return;
        }
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    public Runnable getCancelHandler() {
        return cancelHandler;
    }

    public void setCancelHandler(Runnable cancelHandler) {
        this.cancelHandler = cancelHandler;
    }

    /** @return true when this caller owns the single result callback for the task. */
    public boolean markResultReported() {
        return resultReported.compareAndSet(false, true);
    }

    public boolean isResultReported() {
        return resultReported.get();
    }
}
