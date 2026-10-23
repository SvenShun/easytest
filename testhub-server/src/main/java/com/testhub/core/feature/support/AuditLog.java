package com.testhub.core.feature.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Immutable audit trail entry. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "actor", length = 128)
    private String actor;

    @Column(name = "action", length = 64, nullable = false)
    private String action;

    @Column(name = "target", length = 255)
    private String target;

    @Column(name = "detail", length = 2000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(String id, String actor, String action, String target, String detail, Instant createdAt) {
        this.id = id;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
