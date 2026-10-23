package com.testhub.core.feature.plan;

import java.util.List;

public record TriggerResponse(String runId, List<TriggerItemView> items) {
}
