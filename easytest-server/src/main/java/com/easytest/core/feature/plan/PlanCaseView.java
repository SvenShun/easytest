package com.easytest.core.feature.plan;

import com.easytest.core.feature.casemgmt.ReviewStatus;
import com.easytest.core.feature.script.ScriptType;

import java.util.List;

/** Mounted case projection shown inside a plan. */
public record PlanCaseView(String caseId, String title, ReviewStatus reviewStatus, boolean hasScript,
                           ScriptType scriptType, ExecutorType executorTypeOverride,
                           ExecutorType defaultExecutor, List<ExecutorType> availableExecutors,
                           CaseType caseType, String method, String path) {
}
