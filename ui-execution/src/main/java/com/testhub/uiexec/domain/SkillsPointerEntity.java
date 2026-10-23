package com.testhub.uiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Single-row Skills repository pointer (id is always 1). */
@Entity
@Table(name = "ue_skills_pointer")
public class SkillsPointerEntity {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "git_url", length = 500)
    private String gitUrl;

    @Column(name = "branch", length = 200)
    private String branch;

    @Column(name = "active_commit", length = 100)
    private String activeCommit;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    protected SkillsPointerEntity() {
    }

    public SkillsPointerEntity(Long id, String gitUrl, String branch, String activeCommit, String updatedBy,
                               Instant updatedAt, Instant syncedAt) {
        this.id = id;
        this.gitUrl = gitUrl;
        this.branch = branch;
        this.activeCommit = activeCommit;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
        this.syncedAt = syncedAt;
    }

    public Long getId() {
        return id;
    }

    public String getGitUrl() {
        return gitUrl;
    }

    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getActiveCommit() {
        return activeCommit;
    }

    public void setActiveCommit(String activeCommit) {
        this.activeCommit = activeCommit;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }
}
