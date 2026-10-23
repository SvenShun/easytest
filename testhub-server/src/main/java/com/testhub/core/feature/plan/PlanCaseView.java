package com.testhub.core.feature.plan;

import com.testhub.core.feature.casemgmt.ReviewStatus;
import com.testhub.core.feature.script.ScriptType;

import java.util.List;

/** Mounted case projection shown inside a plan. */
public record PlanCaseView(String caseId, String title, ReviewStatus reviewStatus, boolean hasScript,
                           ScriptType scriptType, ExecutorType executorTypeOverride,
                           ExecutorType defaultExecutor, List<ExecutorType> availableExecutors) {
}
