package com.testhub.core.feature.casemgmt;

import java.time.Instant;
import java.util.List;

/** Full case detail matching contract §1.5. */
public record CaseView(String id, String title, String given, String when, String then, String requirementId,
                       ReviewStatus reviewStatus, CaseSource source, String modelId, CaseScriptView script,
                       String scriptRef, String failureAnalysis, String failureReportUrl, CapabilityView capability,
                       boolean hasScript, List<CaseReviewView> reviews, String createdBy, Instant createdAt,
                       Instant updatedAt) {
}
