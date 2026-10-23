package com.testhub.core.feature.plan;

/** Result callback from ui-execution; idempotent by taskId. */
public record InternalResultRequest(String planRunId, String caseId, String taskId, String status,
                                    String executorType, String failureReason, String failureReportUrl,
                                    String summary) {
}
