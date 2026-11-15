package com.easytest.core.feature.engineeringgit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A Git repository that owns API/UI automation test methods. */
@Entity
@Table(name = "engineering_repo")
public class EngineeringRepo {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "project_id", length = 64)
    private String projectId = "default";

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /** API | UI. */
    @Column(name = "repo_type", length = 16, nullable = false)
    private String repoType;

    @Column(name = "repo_url", length = 1000, nullable = false)
    private String repoUrl;

    @Column(name = "branch_name", length = 128, nullable = false)
    private String branchName = "main";

    @Column(name = "root_directory", length = 500, nullable = false)
    private String rootDirectory = ".";

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_commit", length = 80)
    private String lastCommit;

    @Column(name = "last_scan_at")
    private Instant lastScanAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EngineeringRepo() {
    }

    public EngineeringRepo(String id, String projectId, String name, String repoType, String repoUrl,
                           String branchName, String rootDirectory, String createdBy, Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.repoType = repoType;
        this.repoUrl = repoUrl;
        this.branchName = branchName;
        this.rootDirectory = rootDirectory;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getRepoType() { return repoType; }
    public String getRepoUrl() { return repoUrl; }
    public String getBranchName() { return branchName; }
    public String getRootDirectory() { return rootDirectory; }
    public String getWebhookSecret() { return webhookSecret; }
    public boolean isEnabled() { return enabled; }
    public String getLastCommit() { return lastCommit; }
    public Instant getLastScanAt() { return lastScanAt; }
    public String getLastError() { return lastError; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setRepoType(String repoType) { this.repoType = repoType; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public void setRootDirectory(String rootDirectory) { this.rootDirectory = rootDirectory; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setLastCommit(String lastCommit) { this.lastCommit = lastCommit; }
    public void setLastScanAt(Instant lastScanAt) { this.lastScanAt = lastScanAt; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
