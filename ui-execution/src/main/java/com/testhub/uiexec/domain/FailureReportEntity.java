package com.testhub.uiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Pointer to the generated failure trajectory report (JSON artifact). */
@Entity
@Table(name = "ue_failure_report")
public class FailureReportEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "report_url", length = 1000)
    private String reportUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category", length = 40)
    private FailureCategory reasonCategory;

    @Column(name = "summary", length = 4000)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FailureReportEntity() {
    }

    public FailureReportEntity(String id, String taskId, String reportUrl, FailureCategory reasonCategory,
                               String summary, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.reportUrl = reportUrl;
        this.reasonCategory = reasonCategory;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getReportUrl() {
        return reportUrl;
    }

    public FailureCategory getReasonCategory() {
        return reasonCategory;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
