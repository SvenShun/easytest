package com.testhub.uiexec.client.dto;

/** PUT /internal/v1/cases/{caseId}/failure-analysis. */
public record FailureAnalysisPayload(String taskId, String reportUrl, String summary, String reasonCategory) {
}
