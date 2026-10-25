package com.testhub.core.feature.plan;

public record PlanRunItemView(String caseId, String caseTitle, String taskId, ExecutorType executorType,
                              RunItemStatus status, String failureReason, String failureReportUrl,
                              String summary, String deviceId, int iteration, java.time.Instant updatedAt) {

    public static PlanRunItemView of(PlanRunItem item) {
        return new PlanRunItemView(item.getCaseId(), item.getCaseTitle(), item.getTaskId(),
                item.getExecutorType(), item.getStatus(), item.getFailureReason(),
                item.getFailureReportUrl(), item.getSummary(), item.getDeviceId(), item.getIteration(),
                item.getUpdatedAt());
    }
}
