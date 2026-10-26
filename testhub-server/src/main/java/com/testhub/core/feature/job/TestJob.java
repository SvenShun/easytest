package com.testhub.core.feature.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Scheduled execution of a test plan (Sonic-style job). */
@Entity
@Table(name = "test_job")
public class TestJob {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "plan_id", length = 64, nullable = false)
    private String planId;

    /** Spring 6-field cron expression. */
    @Column(name = "cron_expression", length = 128, nullable = false)
    private String cronExpression;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "device_tags", length = 500)
    private String deviceTags;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_run_id", length = 64)
    private String lastRunId;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TestJob() {
    }

    public TestJob(String id, String name, String planId, String cronExpression, String deviceId,
                   String deviceTags, boolean enabled, String createdBy, Instant createdAt,
                   Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.planId = planId;
        this.cronExpression = cronExpression;
        this.deviceId = deviceId;
        this.deviceTags = deviceTags;
        this.enabled = enabled;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPlanId() { return planId; }
    public String getCronExpression() { return cronExpression; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceTags() { return deviceTags; }
    public boolean isEnabled() { return enabled; }
    public String getLastRunId() { return lastRunId; }
    public Instant getLastRunAt() { return lastRunAt; }
    public Instant getNextRunAt() { return nextRunAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setPlanId(String planId) { this.planId = planId; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public void setDeviceTags(String deviceTags) { this.deviceTags = deviceTags; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setLastRunId(String lastRunId) { this.lastRunId = lastRunId; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
