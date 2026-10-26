package com.testhub.core.feature.casemgmt;

import java.util.List;

/** Case create/update request; {@code steps} is only meaningful for {@link ExecutionMode#STEPS}. */
public record CaseRequest(String title, String given, String when, String then, String requirementId,
                          String scriptId, String source, String executionMode, List<CaseStepRequest> steps) {

    /** Backwards-compatible constructor for callers written before step cases existed. */
    public CaseRequest(String title, String given, String when, String then, String requirementId,
                       String scriptId, String source) {
        this(title, given, when, then, requirementId, scriptId, source, null, null);
    }
}
