package com.testhub.core.feature.job;

import java.time.Instant;

public final class JobDtos {

    private JobDtos() {
    }

    public record JobRequest(String name, String planId, String cronExpression, String deviceId,
                             String deviceTags, Boolean enabled) {
    }

    public record JobView(String id, String name, String planId, String planName, String cronExpression,
                          String deviceId, String deviceTags, boolean enabled, String lastRunId,
                          Instant lastRunAt, Instant nextRunAt, String createdBy, Instant createdAt,
                          Instant updatedAt) {
    }
}
