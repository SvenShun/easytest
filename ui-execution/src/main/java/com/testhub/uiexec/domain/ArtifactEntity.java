package com.testhub.uiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Uploaded result artifact (e.g. junit.xml). Not part of the original V1 table
 * list but needed to persist agent-reported artifacts across restarts; the JSON
 * contract exposes them as the {@code artifacts} array of the task detail.
 */
@Entity
@Table(name = "ue_artifact", indexes = @Index(name = "idx_ue_artifact_task", columnList = "task_id"))
public class ArtifactEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "name", length = 300)
    private String name;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ArtifactEntity() {
    }

    public ArtifactEntity(String id, String taskId, String name, String type, String url, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.name = name;
        this.type = type;
        this.url = url;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
