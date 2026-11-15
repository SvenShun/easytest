package com.easytest.core.feature.apiscenario.git;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One optional Git source for YAML scenario synchronization. */
@Entity
@Table(name = "scenario_git_config")
public class ScenarioGitConfig {

    @Id
    @Column(name = "id", length = 32)
    private String id;

    @Column(name = "repo_url", length = 1000)
    private String repoUrl;

    @Column(name = "branch_name", length = 128)
    private String branchName = "main";

    @Column(name = "yaml_directory", length = 1000)
    private String yamlDirectory = ".";

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "last_commit", length = 80)
    private String lastCommit;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ScenarioGitConfig() {
    }

    public ScenarioGitConfig(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public String getRepoUrl() { return repoUrl; }
    public String getBranchName() { return branchName; }
    public String getYamlDirectory() { return yamlDirectory; }
    public String getWebhookSecret() { return webhookSecret; }
    public boolean isEnabled() { return enabled; }
    public String getLastCommit() { return lastCommit; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public String getLastError() { return lastError; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public void setYamlDirectory(String yamlDirectory) { this.yamlDirectory = yamlDirectory; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setLastCommit(String lastCommit) { this.lastCommit = lastCommit; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
