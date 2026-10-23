package com.testhub.core.feature.plan;

public record TriggerItemView(String caseId, String caseTitle, String taskId, RunItemStatus taskStatus,
                              ExecutorType executorType, String error) {
}
