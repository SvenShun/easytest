package com.testhub.core.feature.plan;

import java.util.List;

/** Mount payload; UI cases and API cases can be mounted in one call. */
public record MountCasesRequest(List<String> caseIds, List<String> apiCaseIds, String executorTypeOverride,
                                List<String> scenarioIds) {

    public MountCasesRequest(List<String> caseIds, List<String> apiCaseIds, String executorTypeOverride) {
        this(caseIds, apiCaseIds, executorTypeOverride, null);
    }
}
