package com.testhub.uiexec.service;

import com.testhub.uiexec.domain.LogEntity;
import com.testhub.uiexec.domain.StepEntity;
import com.testhub.uiexec.domain.TaskEntity;
import com.testhub.uiexec.domain.TaskStatus;
import com.testhub.uiexec.repository.LogRepository;
import com.testhub.uiexec.repository.StepRepository;
import com.testhub.uiexec.repository.TaskRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Simple SSE stream for a task: pushes a {@code status} event on change and
 * {@code step}/{@code log} events for new rows every second. The stream ends
 * when the task reaches a terminal state, the client disconnects, or the
 * 5-minute emitter timeout fires.
 */
@Service
public class TaskStreamService {

    private static final long STREAM_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final int MAX_ITERATIONS = 300;
    private static final int BATCH = 100;

    private final TaskRepository tasks;
    private final StepRepository steps;
    private final LogRepository logs;
    private final TaskService taskService;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "uiexec-sse");
        thread.setDaemon(true);
        return thread;
    });

    public TaskStreamService(TaskRepository tasks, StepRepository steps, LogRepository logs,
                             TaskService taskService) {
        this.tasks = tasks;
        this.steps = steps;
        this.logs = logs;
        this.taskService = taskService;
    }

    public SseEmitter open(String taskId) {
        taskService.requireTask(taskId);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitter.complete());
        executor.execute(() -> stream(taskId, emitter));
        return emitter;
    }

    private void stream(String taskId, SseEmitter emitter) {
        int lastStepSeq = 0;
        int lastLogSeq = 0;
        TaskStatus lastStatus = null;
        try {
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                TaskEntity task = tasks.findById(taskId).orElse(null);
                if (task == null) {
                    emitter.complete();
                    return;
                }
                if (lastStatus != task.getStatus()) {
                    lastStatus = task.getStatus();
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("taskId", task.getId());
                    payload.put("status", task.getStatus().name());
                    payload.put("executorType", task.getExecutorType() == null ? null
                            : task.getExecutorType().name());
                    payload.put("failureReason", task.getFailureReason() == null ? null
                            : task.getFailureReason().name());
                    payload.put("finishedAt", task.getFinishedAt());
                    emitter.send(SseEmitter.event().name("status")
                            .data(payload, MediaType.APPLICATION_JSON));
                }

                List<StepEntity> newSteps = steps.findByTaskIdAndSeqGreaterThanOrderBySeqAsc(
                        taskId, lastStepSeq, PageRequest.of(0, BATCH));
                for (StepEntity step : newSteps) {
                    lastStepSeq = Math.max(lastStepSeq, step.getSeq());
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("stepId", step.getId());
                    payload.put("seq", step.getSeq());
                    payload.put("status", step.getStatus());
                    payload.put("decision", step.getDecision());
                    payload.put("action", step.getAction());
                    payload.put("elapsedMs", step.getElapsedMs());
                    payload.put("screenshotUrl", step.getScreenshotUrl());
                    payload.put("createdAt", step.getCreatedAt());
                    emitter.send(SseEmitter.event().name("step")
                            .data(payload, MediaType.APPLICATION_JSON));
                }

                List<LogEntity> newLogs = logs.findByTaskIdAndSeqGreaterThanOrderBySeqAsc(
                        taskId, lastLogSeq, PageRequest.of(0, BATCH));
                for (LogEntity logEntity : newLogs) {
                    lastLogSeq = Math.max(lastLogSeq, logEntity.getSeq());
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("seq", logEntity.getSeq());
                    payload.put("level", logEntity.getLevel());
                    payload.put("message", logEntity.getMessage());
                    payload.put("createdAt", logEntity.getCreatedAt());
                    emitter.send(SseEmitter.event().name("log")
                            .data(payload, MediaType.APPLICATION_JSON));
                }

                if (task.getStatus().isTerminal()) {
                    emitter.complete();
                    return;
                }
                Thread.sleep(1000);
            }
            emitter.complete();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (Exception e) {
            // Client disconnected (send fails) or the emitter timed out.
            emitter.complete();
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
