package com.easytest.core.feature.engineering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Platform-side registry of test-engineering contract methods. */
@Entity
@Table(name = "engineering_method", uniqueConstraints = @UniqueConstraint(
        name = "uk_engineering_method_path", columnNames = "method_path"))
public class EngineeringMethod {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "contract_id", length = 128)
    private String contractId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "method_path", length = 1000, nullable = false)
    private String methodPath;

    @Column(name = "inputs_json", columnDefinition = "TEXT")
    private String inputsJson;

    @Column(name = "outputs_json", columnDefinition = "TEXT")
    private String outputsJson;

    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "project_id", length = 64)
    private String projectId = "default";

    @Column(name = "repo_id", length = 64)
    private String repoId;

    /** API | UI. */
    @Column(name = "kind", length = 16)
    private String kind = "API";

    /** UI steps / artifact / script metadata for repository methods. */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EngineeringMethod() {
    }

    public EngineeringMethod(String id, String contractId, String name, String description, String methodPath,
                             String inputsJson, String outputsJson, String tagsJson, String source,
                             Instant updatedAt) {
        this.id = id;
        this.contractId = contractId;
        this.name = name;
        this.description = description;
        this.methodPath = methodPath;
        this.inputsJson = inputsJson;
        this.outputsJson = outputsJson;
        this.tagsJson = tagsJson;
        this.source = source;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getContractId() { return contractId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getMethodPath() { return methodPath; }
    public String getInputsJson() { return inputsJson; }
    public String getOutputsJson() { return outputsJson; }
    public String getTagsJson() { return tagsJson; }
    public String getSource() { return source; }
    public String getProjectId() { return projectId; }
    public String getRepoId() { return repoId; }
    public String getKind() { return kind; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setContractId(String contractId) { this.contractId = contractId; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setMethodPath(String methodPath) { this.methodPath = methodPath; }
    public void setInputsJson(String inputsJson) { this.inputsJson = inputsJson; }
    public void setOutputsJson(String outputsJson) { this.outputsJson = outputsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public void setSource(String source) { this.source = source; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public void setRepoId(String repoId) { this.repoId = repoId; }
    public void setKind(String kind) { this.kind = kind; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
