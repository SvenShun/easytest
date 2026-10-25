package com.testhub.core.feature.plan;

import java.time.Instant;

public record PlanView(String id, String name, String description, String status, String createdBy,
                       String environmentId, Instant createdAt, Instant updatedAt) {

    public static PlanView of(TestPlan plan) {
        return new PlanView(plan.getId(), plan.getName(), plan.getDescription(), plan.getStatus(),
                plan.getCreatedBy(), plan.getEnvironmentId(), plan.getCreatedAt(), plan.getUpdatedAt());
    }
}
