package com.easytest.core.feature.script;

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

    @Column(name = "contract_id", length = 128)
    private String contractId;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "input_contract_json", columnDefinition = "TEXT")
    private String inputContractJson;

    @Column(name = "output_contract_json", columnDefinition = "TEXT")
    private String outputContractJson;

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

    public java.util.List<String> getInputs() { return readList(inputContractJson); }
    public java.util.List<String> getOutputs() { return readList(outputContractJson); }

    private static java.util.List<String> readList(String json) {
        if (json == null || json.isBlank()) return java.util.List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() { });
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    public String getContractId() { return contractId; }
    public String getDescription() { return description; }
    public String getInputContractJson() { return inputContractJson; }
    public String getOutputContractJson() { return outputContractJson; }

    public void setContractId(String contractId) { this.contractId = contractId; }
    public void setDescription(String description) { this.description = description; }
    public void setInputContractJson(String inputContractJson) { this.inputContractJson = inputContractJson; }
    public void setOutputContractJson(String outputContractJson) { this.outputContractJson = outputContractJson; }

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
