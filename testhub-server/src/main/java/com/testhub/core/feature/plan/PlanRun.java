package com.testhub.core.feature.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One trigger of a test plan. Counts are recomputed from the items. */
@Entity
@Table(name = "plan_run")
public class PlanRun {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "plan_id", length = 64, nullable = false)
    private String planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private RunStatus status;

    @Column(name = "triggered_by", length = 64)
    private String triggeredBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total", nullable = false)
    private int total;

    @Column(name = "success", nullable = false)
    private int success;

    @Column(name = "failed", nullable = false)
    private int failed;

    @Column(name = "timeout", nullable = false)
    private int timeout;

    @Column(name = "cancelled", nullable = false)
    private int cancelled;

    @Column(name = "running", nullable = false)
    private int running;

    @Column(name = "pending", nullable = false)
    private int pending;

    @Column(name = "skipped", nullable = false)
    private int skipped;

    @Column(name = "notification_sent", nullable = false)
    private boolean notificationSent;

    protected PlanRun() {
    }

    public PlanRun(String id, String planId, RunStatus status, String triggeredBy, Instant startedAt) {
        this.id = id;
        this.planId = planId;
        this.status = status;
        this.triggeredBy = triggeredBy;
        this.startedAt = startedAt;
        this.total = 0;
        this.success = 0;
        this.failed = 0;
        this.timeout = 0;
        this.cancelled = 0;
        this.running = 0;
        this.pending = 0;
        this.skipped = 0;
    }

    public String getId() {
        return id;
    }

    public String getPlanId() {
        return planId;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getCancelled() {
        return cancelled;
    }

    public void setCancelled(int cancelled) {
        this.cancelled = cancelled;
    }

    public int getRunning() {
        return running;
    }

    public void setRunning(int running) {
        this.running = running;
    }

    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    public boolean isNotificationSent() { return notificationSent; }
    public void setNotificationSent(boolean notificationSent) { this.notificationSent = notificationSent; }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }
}
