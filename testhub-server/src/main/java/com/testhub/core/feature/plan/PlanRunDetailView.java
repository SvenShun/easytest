package com.testhub.core.feature.plan;

import java.util.List;

public record PlanRunDetailView(PlanRunView run, List<PlanRunItemView> items) {
}
