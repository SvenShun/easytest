package com.testhub.core.feature.casemgmt;

/** Failure analysis write-back from ui-execution. */
public record FailureAnalysisRequest(String taskId, String reportUrl, String summary, String reasonCategory) {
}
