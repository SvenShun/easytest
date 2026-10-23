package com.testhub.core.feature.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** One requirement-to-BDD analysis run. */
@Entity
@Table(name = "ai_job")
public class AiJob {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "requirement_id", length = 64)
    private String requirementId;

    @Column(name = "model_id", length = 128)
    private String modelId;

    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private AiJobStatus status;

    /** Comma separated case ids; UUIDs never contain a comma. */
    @Lob
    @Column(name = "output_case_ids")
    private String outputCaseIds;

    @Lob
    @Column(name = "error")
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected AiJob() {
    }

    public AiJob(String id, String requirementId, String modelId, String promptVersion, AiJobStatus status,
                 Instant createdAt) {
        this.id = id;
        this.requirementId = requirementId;
        this.modelId = modelId;
        this.promptVersion = promptVersion;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getRequirementId() {
        return requirementId;
    }

    public String getModelId() {
        return modelId;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public AiJobStatus getStatus() {
        return status;
    }

    public void setStatus(AiJobStatus status) {
        this.status = status;
    }

    public String getOutputCaseIds() {
        return outputCaseIds;
    }

    public void setOutputCaseIds(String outputCaseIds) {
        this.outputCaseIds = outputCaseIds;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
