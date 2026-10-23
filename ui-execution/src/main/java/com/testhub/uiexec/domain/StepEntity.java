package com.testhub.uiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** One trajectory step reported by the agent / AI runtime. Unique per (taskId, seq). */
@Entity
@Table(name = "ue_step", uniqueConstraints = @UniqueConstraint(
        name = "uk_ue_step_task_seq", columnNames = {"task_id", "seq"}))
public class StepEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "decision", length = 4000)
    private String decision;

    @Column(name = "action", length = 2000)
    private String action;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    @Column(name = "screenshot_url", length = 1000)
    private String screenshotUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StepEntity() {
    }

    public StepEntity(String id, String taskId, int seq, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.seq = seq;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getScreenshotUrl() {
        return screenshotUrl;
    }

    public void setScreenshotUrl(String screenshotUrl) {
        this.screenshotUrl = screenshotUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
