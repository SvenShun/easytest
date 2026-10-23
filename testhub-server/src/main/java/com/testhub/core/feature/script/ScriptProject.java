package com.testhub.core.feature.script;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Uploaded script bundle (zip) referenced by client cases. */
@Entity
@Table(name = "script_project")
public class ScriptProject {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "script_type", length = 32, nullable = false)
    private ScriptType scriptType;

    @Column(name = "entry_command", length = 512)
    private String entryCommand;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "size_bytes")
    private long sizeBytes;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ScriptProject() {
    }

    public ScriptProject(String id, String name, ScriptType scriptType, String entryCommand, String objectKey,
                         long sizeBytes, String createdBy, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.scriptType = scriptType;
        this.entryCommand = entryCommand;
        this.objectKey = objectKey;
        this.sizeBytes = sizeBytes;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ScriptType getScriptType() {
        return scriptType;
    }

    public String getEntryCommand() {
        return entryCommand;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
