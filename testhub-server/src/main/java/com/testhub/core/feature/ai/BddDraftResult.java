package com.testhub.core.feature.ai;

/** Created case summary returned in the generation response. */
public record BddDraftResult(String caseId, String title, String given, String when, String then,
                             String reviewStatus) {
}
