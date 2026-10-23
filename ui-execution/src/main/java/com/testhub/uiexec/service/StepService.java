package com.testhub.uiexec.service;

import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.uiexec.artifact.ArtifactStore;
import com.testhub.uiexec.domain.StepEntity;
import com.testhub.uiexec.domain.TaskEntity;
import com.testhub.uiexec.domain.TaskStatus;
import com.testhub.uiexec.repository.StepRepository;
import com.testhub.uiexec.repository.TaskRepository;
import com.testhub.uiexec.web.dto.TaskDtos.StepCallbackRequest;
import com.testhub.uiexec.web.dto.TaskDtos.StepCallbackResponse;
import com.testhub.uiexec.web.dto.TaskDtos.StepView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Handles agent step callbacks: screenshot upload, idempotent {@code (taskId,
 * seq)} upsert and the DISPATCHED → RUNNING transition on the first step.
 */
@Service
public class StepService {

    private static final Logger log = LoggerFactory.getLogger(StepService.class);
    private static final int MAX_SCREENSHOT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_STEPS = 500;

    private final StepRepository steps;
    private final TaskRepository tasks;
    private final ArtifactStore artifactStore;
    private final ExecutionLock lock;

    public StepService(StepRepository steps, TaskRepository tasks, ArtifactStore artifactStore, ExecutionLock lock) {
        this.steps = steps;
        this.tasks = tasks;
        this.artifactStore = artifactStore;
        this.lock = lock;
    }

    public StepCallbackResponse upsert(String taskId, StepCallbackRequest request) {
        if (request.seq() <= 0) {
            throw ApiException.badRequest("seq 必须大于 0");
        }
        lock.lock();
        try {
            TaskEntity task = tasks.findById(taskId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
            if (task.getStatus().isTerminal()) {
                throw new ApiException(HttpStatus.CONFLICT, "TASK_ALREADY_FINISHED", "任务已结束，不能再上报步骤");
            }

            String screenshotUrl = storeScreenshot(taskId, request);
            StepEntity step = steps.findByTaskIdAndSeq(taskId, request.seq())
                    .orElseGet(() -> new StepEntity(Ids.newId("step"), taskId, request.seq(), Instant.now()));
            step.setStatus(truncate(request.status(), 32));
            step.setDecision(truncate(request.decision(), 4000));
            step.setAction(truncate(request.action(), 2000));
            step.setElapsedMs(request.elapsedMs());
            if (screenshotUrl != null) {
                step.setScreenshotUrl(screenshotUrl);
            }
            StepEntity saved = steps.save(step);

            if (task.getStatus() == TaskStatus.DISPATCHED) {
                task.setStatus(TaskStatus.RUNNING);
                if (task.getStartedAt() == null) {
                    task.setStartedAt(Instant.now());
                }
                tasks.save(task);
            }
            return new StepCallbackResponse(saved.getId(), saved.getScreenshotUrl());
        } finally {
            lock.unlock();
        }
    }

    public List<StepView> list(String taskId) {
        return steps.findByTaskIdOrderBySeqAsc(taskId, PageRequest.of(0, MAX_STEPS)).stream()
                .map(this::toView)
                .toList();
    }

    public StepView toView(StepEntity step) {
        return new StepView(step.getId(), step.getSeq(), step.getStatus(), step.getDecision(), step.getAction(),
                step.getElapsedMs(), step.getScreenshotUrl(), step.getCreatedAt());
    }

    private String storeScreenshot(String taskId, StepCallbackRequest request) {
        if (request.screenshotBase64() == null || request.screenshotBase64().isBlank()) {
            return null;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(request.screenshotBase64());
        } catch (IllegalArgumentException e) {
            log.warn("忽略非法的截图 base64 taskId={} seq={}", taskId, request.seq());
            return null;
        }
        if (decoded.length > MAX_SCREENSHOT_BYTES) {
            log.warn("忽略超过 2MB 的截图 taskId={} seq={} bytes={}", taskId, request.seq(), decoded.length);
            return null;
        }
        try {
            String relative = artifactStore.put(taskId, "steps/" + request.seq() + ".png", decoded);
            return artifactStore.publicUrl(relative);
        } catch (RuntimeException e) {
            // Keep the step even when the artifact store is unavailable.
            log.warn("截图保存失败 taskId={} seq={}: {}", taskId, request.seq(), e.getMessage());
            return null;
        }
    }

    private String truncate(String value, int max) {
        return value == null ? null : value.length() <= max ? value : value.substring(0, max);
    }
}
