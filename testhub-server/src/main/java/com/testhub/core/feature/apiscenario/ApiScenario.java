package com.testhub.core.feature.apiscenario;

import jakarta.persistence.*;
import java.time.Instant;

/** A YAML-driven multi-step API scenario. */
@Entity
@Table(name = "api_scenario")
public class ApiScenario {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "yaml_text", columnDefinition = "TEXT", nullable = false)
    private String yamlText;

    @Column(name = "environment_id", length = 64)
    private String environmentId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApiScenario() {
    }

    public ApiScenario(String id, String name, String description, String yamlText, String environmentId,
                       boolean enabled, String createdBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.yamlText = yamlText;
        this.environmentId = environmentId;
        this.enabled = enabled;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getYamlText() { return yamlText; }
    public String getEnvironmentId() { return environmentId; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setYamlText(String yamlText) { this.yamlText = yamlText; }
    public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
