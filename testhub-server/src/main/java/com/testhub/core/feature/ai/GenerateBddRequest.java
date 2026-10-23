package com.testhub.core.feature.ai;

/** Request body of {@code POST /api/v1/ai/bdd-drafts}. */
public record GenerateBddRequest(String requirementId, String requirementText, Integer sampleCount) {
}
