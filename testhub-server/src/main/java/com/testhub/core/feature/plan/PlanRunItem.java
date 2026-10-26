package com.testhub.core.feature.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** Per-case execution result inside a plan run. */
@Entity
@Table(name = "plan_run_item")
public class PlanRunItem {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "case_id", length = 64)
    private String caseId;

    @Column(name = "case_title", length = 255)
    private String caseTitle;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "executor_type", length = 16)
    private ExecutorType executorType;

    /** 1-based data-driven iteration; 0 for a normal single execution. */
    @Column(name = "iteration")
    private Integer iteration = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private RunItemStatus status;

    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(name = "failure_report_url", length = 1024)
    private String failureReportUrl;

    @Lob
    @Column(name = "summary")
    private String summary;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Lob
    @Column(name = "error")
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PlanRunItem() {
    }

    public PlanRunItem(String id, String runId, String caseId, String caseTitle, ExecutorType executorType,
                       RunItemStatus status, Instant createdAt) {
        this(id, runId, caseId, caseTitle, executorType, status, createdAt, 0);
    }

    public PlanRunItem(String id, String runId, String caseId, String caseTitle, ExecutorType executorType,
                       RunItemStatus status, Instant createdAt, int iteration) {
        this.id = id;
        this.runId = runId;
        this.caseId = caseId;
        this.caseTitle = caseTitle;
        this.executorType = executorType;
        this.status = status;
        this.createdAt = createdAt;
        this.iteration = iteration;
        this.updatedAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getRunId() {
        return runId;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getCaseTitle() {
        return caseTitle;
    }

    public void setCaseTitle(String caseTitle) {
        this.caseTitle = caseTitle;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public int getIteration() {
        return iteration == null ? 0 : iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public ExecutorType getExecutorType() {
        return executorType;
    }

    public void setExecutorType(ExecutorType executorType) {
        this.executorType = executorType;
    }

    public RunItemStatus getStatus() {
        return status;
    }

    public void setStatus(RunItemStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getFailureReportUrl() {
        return failureReportUrl;
    }

    public void setFailureReportUrl(String failureReportUrl) {
        this.failureReportUrl = failureReportUrl;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
