package com.testhub.uiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Local audit trail for ui-execution configuration changes. */
@Entity
@Table(name = "ue_audit_log", indexes = @Index(name = "idx_ue_audit_created", columnList = "created_at"))
public class AuditLogEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "actor", length = 100)
    private String actor;

    @Column(name = "action", length = 100)
    private String action;

    @Column(name = "target", length = 500)
    private String target;

    @Column(name = "detail", length = 2000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLogEntity() {
    }

    public AuditLogEntity(String id, String actor, String action, String target, String detail, Instant createdAt) {
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
