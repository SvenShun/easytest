package com.easytest.core.feature.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Department-level project/workspace boundary. */
@Entity
@Table(name = "project")
public class Project {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "project_key", length = 64)
    private String key;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Project() {
    }

    public Project(String id, String name, String key, String description, String createdBy,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.key = key;
        this.description = description;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getKey() { return key; }
    public String getDescription() { return description; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setKey(String key) { this.key = key; }
    public void setDescription(String description) { this.description = description; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
