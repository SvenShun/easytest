package com.testhub.core.feature.caselink;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Lightweight marker: a text/BDD case can point at API/UI automation assets. */
@Entity
@Table(name = "case_automation_link", uniqueConstraints = @UniqueConstraint(
        name = "uk_case_automation_link", columnNames = {"client_case_id", "target_type", "target_id"}))
public class CaseAutomationLink {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "client_case_id", length = 64, nullable = false)
    private String clientCaseId;

    /** API_CASE / API_SCENARIO / UI_CASE */
    @Column(name = "target_type", length = 32, nullable = false)
    private String targetType;

    @Column(name = "target_id", length = 64, nullable = false)
    private String targetId;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaseAutomationLink() {
    }

    public CaseAutomationLink(String id, String clientCaseId, String targetType, String targetId,
                              String createdBy, Instant createdAt) {
        this.id = id;
        this.clientCaseId = clientCaseId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getClientCaseId() { return clientCaseId; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
