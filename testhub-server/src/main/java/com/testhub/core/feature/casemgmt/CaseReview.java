package com.testhub.core.feature.casemgmt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One review transition record. */
@Entity
@Table(name = "case_review")
public class CaseReview {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "case_id", length = 64, nullable = false)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 16, nullable = false)
    private ReviewAction action;

    @Column(name = "actor", length = 64)
    private String actor;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaseReview() {
    }

    public CaseReview(String id, String caseId, ReviewAction action, String actor, String comment,
                      Instant createdAt) {
        this.id = id;
        this.caseId = caseId;
        this.action = action;
        this.actor = actor;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getCaseId() {
        return caseId;
    }

    public ReviewAction getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
