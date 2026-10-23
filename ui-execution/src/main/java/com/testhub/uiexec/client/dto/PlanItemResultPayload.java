package com.testhub.uiexec.client.dto;

/** POST /internal/v1/plan-runs/items/result. */
public record PlanItemResultPayload(String planRunId, String caseId, String taskId, String status,
                                    String executorType, String failureReason, String failureReportUrl,
                                    String summary) {
}
