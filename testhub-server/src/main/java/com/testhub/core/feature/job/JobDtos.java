package com.testhub.core.feature.job;

import java.time.Instant;

public final class JobDtos {

    private JobDtos() {
    }

    public record JobRequest(String name, String planId, String cronExpression, String deviceId,
                             String deviceTags, Boolean enabled, java.util.List<String> deviceIds) {

        public JobRequest(String name, String planId, String cronExpression, String deviceId,
                          String deviceTags, Boolean enabled) {
            this(name, planId, cronExpression, deviceId, deviceTags, enabled, null);
        }
    }

    public record JobView(String id, String name, String planId, String planName, String cronExpression,
                          String deviceId, String deviceTags, java.util.List<String> deviceIds,
                          boolean enabled, String lastRunId,
                          Instant lastRunAt, Instant nextRunAt, String createdBy, Instant createdAt,
                          Instant updatedAt) {
    }
}
