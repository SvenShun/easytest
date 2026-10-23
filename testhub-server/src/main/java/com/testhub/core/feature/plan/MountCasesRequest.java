package com.testhub.core.feature.plan;

import java.util.List;

/** Mount payload; the executor override applies to every listed case. */
public record MountCasesRequest(List<String> caseIds, String executorTypeOverride) {
}
