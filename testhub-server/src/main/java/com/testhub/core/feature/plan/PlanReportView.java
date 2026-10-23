package com.testhub.core.feature.plan;

import java.util.List;

public record PlanReportView(PlanRunView run, RunSummary summary, List<ExecutorStats> executors,
                             List<FailureCategory> failureCategories, List<PlanRunItemView> items) {
}
