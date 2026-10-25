package io.metersphere.uiexec;

import java.util.List;

/** Request/response payloads of the embedded UI execution module. */
public final class UiExecutionDtos {

    private UiExecutionDtos() {
    }

    public record CreateTaskRequest(String caseId, String testPlanId, String testPlanCaseId,
                                    String projectId, String deviceSerial,
                                    Integer timeoutSec, Integer stepTimeoutSec) {
    }

    public record StepCallback(int seq, String status, String decision, String action, Long elapsedMs) {
    }

    public record ResultCallback(String status, String failureReason, String reasonCategory, String summary) {
    }

    public record AssociateRequest(String testPlanId, String caseId) {
    }

    public record StepView(String id, int seq, String status, String decision, String action, Long elapsedMs) {
    }

    public record TaskView(String id, String projectId, String caseId, String testPlanId, String testPlanCaseId,
                           String caseTitle, String given, String when, String then,
                           String deviceSerial, String taskId, String status, String summary,
                           String failureReason, String reportUrl, String createdBy,
                           int timeoutSec, int stepTimeoutSec, Integer queuePosition, Long dispatchedAt,
                           Long finishedAt, long createdAt, long updatedAt, List<StepView> steps) {
    }
}
