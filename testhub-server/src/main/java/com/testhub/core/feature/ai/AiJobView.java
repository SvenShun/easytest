package com.testhub.core.feature.ai;

import java.time.Instant;
import java.util.List;

public record AiJobView(String id, String requirementId, String modelId, String promptVersion, String status,
                        List<String> outputCaseIds, String error, Instant createdAt, Instant finishedAt) {

    public static AiJobView of(AiJob job) {
        List<String> caseIds = job.getOutputCaseIds() == null || job.getOutputCaseIds().isBlank()
                ? List.of()
                : List.of(job.getOutputCaseIds().split(","));
        return new AiJobView(job.getId(), job.getRequirementId(), job.getModelId(), job.getPromptVersion(),
                job.getStatus().name(), caseIds, job.getError(), job.getCreatedAt(), job.getFinishedAt());
    }
}
