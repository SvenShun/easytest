package com.testhub.core.feature.apienv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Reusable API environment: base URL, headers, variables and auth preset. */
@Entity
@Table(name = "api_environment")
public class ApiEnvironment {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "base_url", length = 1000, nullable = false)
    private String baseUrl;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;

    @Column(name = "auth_type", length = 16, nullable = false)
    private String authType = "NONE";

    @Column(name = "auth_json", columnDefinition = "TEXT")
    private String authJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApiEnvironment() {
    }

    public ApiEnvironment(String id, String name, String baseUrl, String headersJson, String variablesJson,
                          String authType, String authJson, boolean enabled, String createdBy,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
        this.headersJson = headersJson;
        this.variablesJson = variablesJson;
        this.authType = authType;
        this.authJson = authJson;
        this.enabled = enabled;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getHeadersJson() { return headersJson; }
    public String getVariablesJson() { return variablesJson; }
    public String getAuthType() { return authType; }
    public String getAuthJson() { return authJson; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }
    public void setVariablesJson(String variablesJson) { this.variablesJson = variablesJson; }
    public void setAuthType(String authType) { this.authType = authType; }
    public void setAuthJson(String authJson) { this.authJson = authJson; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
