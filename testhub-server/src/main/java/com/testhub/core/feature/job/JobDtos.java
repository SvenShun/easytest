package com.testhub.core.feature.job;

import java.time.Instant;

public final class JobDtos {

    private JobDtos() {
    }

    public record JobRequest(String name, String planId, String cronExpression, String deviceId,
                             String deviceTags, Boolean enabled, java.util.List<String> deviceIds,
                             String scenarioId) {

        public JobRequest(String name, String planId, String cronExpression, String deviceId,
                          String deviceTags, Boolean enabled) {
            this(name, planId, cronExpression, deviceId, deviceTags, enabled, null, null);
        }

        public JobRequest(String name, String planId, String cronExpression, String deviceId,
                          String deviceTags, Boolean enabled, java.util.List<String> deviceIds) {
            this(name, planId, cronExpression, deviceId, deviceTags, enabled, deviceIds, null);
        }
    }

    public record JobView(String id, String name, String planId, String planName, String scenarioId,
                          String scenarioName, String cronExpression, String deviceId, String deviceTags,
                          java.util.List<String> deviceIds, boolean enabled, String lastRunId,
                          Instant lastRunAt, Instant nextRunAt, String createdBy, Instant createdAt,
                          Instant updatedAt) {
    }
}
