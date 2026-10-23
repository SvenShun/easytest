package com.testhub.core.feature.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Text / vision model configuration. API keys are stored AES-GCM encrypted. */
@Entity
@Table(name = "model_config")
public class ModelConfig {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    /** {@code text} or {@code vision}. */
    @Column(name = "config_type", length = 16, nullable = false, unique = true)
    private String configType;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "endpoint", length = 512)
    private String endpoint;

    @Column(name = "api_key_enc", length = 2000)
    private String apiKeyEnc;

    @Column(name = "model_id", length = 128)
    private String modelId;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ModelConfig() {
    }

    public ModelConfig(String id, String configType, String provider, String endpoint, String apiKeyEnc,
                       String modelId, String updatedBy, Instant updatedAt) {
        this.id = id;
        this.configType = configType;
        this.provider = provider;
        this.endpoint = endpoint;
        this.apiKeyEnc = apiKeyEnc;
        this.modelId = modelId;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getConfigType() {
        return configType;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKeyEnc() {
        return apiKeyEnc;
    }

    public void setApiKeyEnc(String apiKeyEnc) {
        this.apiKeyEnc = apiKeyEnc;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
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
}
