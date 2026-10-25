package com.testhub.apiexec.client;

import com.testhub.apiexec.domain.ApiTaskEntity;

/**
 * POST {core}/internal/v1/plan-runs/items/result payload (contract §3.3).
 * {@code failureReportUrl} is absolute and rooted at the configured public base URL.
 */
public record PlanItemResultPayload(String planRunId, String caseId, String taskId, String status,
                                    String executorType, String failureReason, String failureReportUrl,
                                    String summary) {

    public static PlanItemResultPayload from(ApiTaskEntity task, String publicBaseUrl) {
        return new PlanItemResultPayload(
                task.getPlanRunId(),
                task.getCaseId(),
                task.getId(),
                task.getStatus() == null ? null : task.getStatus().name(),
                "API",
                task.getFailureReason(),
                reportUrl(publicBaseUrl, task.getId()),
                task.getSummary());
    }

    public static String reportUrl(String publicBaseUrl, String taskId) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/v1/api-execution/tasks/" + taskId + "/report";
    }
}
