package com.testhub.core.feature.plan;

import java.util.List;

public record PlanDetailView(PlanView plan, List<PlanCaseView> cases, List<PlanRunView> runs,
                             List<String> deviceIds) {
}
