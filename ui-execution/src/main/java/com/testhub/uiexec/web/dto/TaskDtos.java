package com.testhub.uiexec.web.dto;

import com.testhub.uiexec.domain.ExecutorType;
import com.testhub.uiexec.domain.FailureCategory;
import com.testhub.uiexec.domain.StepSpec;
import com.testhub.uiexec.domain.TaskStatus;

import java.time.Instant;
import java.util.List;

/** Task-related request / response shapes (docs/API-CONTRACT.md §2.1–2.3). */
public final class TaskDtos {

    private TaskDtos() {
    }

    public record TargetDeviceSelector(String deviceId, String agentId, List<String> tags) {
    }

    public record CreateTaskRequest(String planId, String planRunId, String caseId, ExecutorType executorType,
                                    TargetDeviceSelector targetDeviceSelector, Integer timeoutSec,
                                    Integer stepTimeoutSec, String createdBy, List<StepSpec> steps,
                                    String caseTitle) {

        /** Backwards-compatible constructor for AI/SCRIPT callers written before STEP support. */
        public CreateTaskRequest(String planId, String planRunId, String caseId, ExecutorType executorType,
                                 TargetDeviceSelector targetDeviceSelector, Integer timeoutSec,
                                 Integer stepTimeoutSec, String createdBy) {
            this(planId, planRunId, caseId, executorType, targetDeviceSelector, timeoutSec, stepTimeoutSec,
                    createdBy, null, null);
        }
    }

    public record CreateTaskResponse(String taskId, TaskStatus status, ExecutorType executorType, String deviceId,
                                     String deviceSerial, int queuePosition, Instant createdAt) {
    }

    public record CancelTaskResponse(String taskId, TaskStatus status) {
    }

    public record StepCallbackRequest(int seq, String status, String decision, String action, Long elapsedMs,
                                      String screenshotBase64, String screenshotContentType) {
    }

    public record StepCallbackResponse(String stepId, String screenshotUrl) {
    }

    public record LogLine(String level, String message) {
    }

    public record LogBatchRequest(List<LogLine> lines) {
    }

    public record ResultArtifact(String name, String type, String contentBase64) {
    }

    public record TaskResultRequest(TaskStatus status, String failureReason, String summary, String reasonCategory,
                                    List<ResultArtifact> artifacts) {
    }

    public record TaskResultResponse(String taskId, TaskStatus status, String failureReportUrl) {
    }

    public record BddView(String given, String when, String then) {
    }

    public record SkillsRefView(String repo, String commit) {
    }

    public record StepView(String stepId, int seq, String status, String decision, String action, Long elapsedMs,
                           String screenshotUrl, Instant createdAt) {
    }

    public record LogView(int seq, String level, String message, Instant createdAt) {
    }

    public record ArtifactView(String name, String type, String url) {
    }

    public record FailureReportView(String reportUrl, String reasonCategory, String summary) {
    }

    public record TaskDetailView(String taskId, String planId, String planRunId, String caseId, String caseTitle,
                                 BddView bdd, ExecutorType executorType, TaskStatus status,
                                 FailureCategory failureReason, String failureSummary, String failureReportUrl,
                                 int queuePosition, String deviceId, String deviceSerial, String agentId,
                                 SkillsRefView skills, int timeoutSec, int stepTimeoutSec, Instant createdAt,
                                 Instant dispatchedAt, Instant startedAt, Instant finishedAt, List<StepView> steps,
                                 List<LogView> logs, List<ArtifactView> artifacts,
                                 FailureReportView failureReport) {
    }

    public record TaskSummaryView(String taskId, String planId, String planRunId, String caseId, String caseTitle,
                                  ExecutorType executorType, TaskStatus status, FailureCategory failureReason,
                                  String failureSummary, String deviceId, String deviceSerial, String agentId,
                                  int queuePosition, Instant createdAt, Instant dispatchedAt, Instant startedAt,
                                  Instant finishedAt) {
    }
}
