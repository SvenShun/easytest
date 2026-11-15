package com.easytest.core.feature.apicase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** API test case: an HTTP request plus assertions. */
@Entity
@Table(name = "api_case")
public class ApiCase {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "project_id", length = 64)
    private String projectId = "default";

    @Column(name = "method", length = 10, nullable = false)
    private String method;

    @Column(name = "base_url", length = 1000)
    private String baseUrl;

    @Column(name = "path", length = 2000, nullable = false)
    private String path;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    @Column(name = "query_json", columnDefinition = "TEXT")
    private String queryJson;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "assertions_json", columnDefinition = "TEXT")
    private String assertionsJson;

    /** Optional data rows; each row produces one execution iteration. */
    @Column(name = "dataset_json", columnDefinition = "TEXT")
    private String datasetJson;

    /** Variables this reusable component expects from upstream (name -> description). */
    @Column(name = "input_contract_json", columnDefinition = "TEXT")
    private String inputContractJson;

    /** Variables this component produces for downstream steps (name -> description/source). */
    @Column(name = "output_contract_json", columnDefinition = "TEXT")
    private String outputContractJson;

    @Column(name = "timeout_sec", nullable = false)
    private int timeoutSec = 30;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApiCase() {
    }

    public ApiCase(String id, String name, String method, String baseUrl, String path, String headersJson,
                   String queryJson, String body, String assertionsJson, String datasetJson,
                   String inputContractJson, String outputContractJson,
                   int timeoutSec, boolean enabled, String createdBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.method = method;
        this.baseUrl = baseUrl;
        this.path = path;
        this.headersJson = headersJson;
        this.queryJson = queryJson;
        this.body = body;
        this.assertionsJson = assertionsJson;
        this.datasetJson = datasetJson;
        this.inputContractJson = inputContractJson;
        this.outputContractJson = outputContractJson;
        this.timeoutSec = timeoutSec;
        this.enabled = enabled;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getMethod() { return method; }
    public String getBaseUrl() { return baseUrl; }
    public String getPath() { return path; }
    public String getHeadersJson() { return headersJson; }
    public String getQueryJson() { return queryJson; }
    public String getBody() { return body; }
    public String getAssertionsJson() { return assertionsJson; }
    public String getDatasetJson() { return datasetJson; }
    public String getInputContractJson() { return inputContractJson; }
    public String getOutputContractJson() { return outputContractJson; }
    public int getTimeoutSec() { return timeoutSec; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setMethod(String method) { this.method = method; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setPath(String path) { this.path = path; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }
    public void setQueryJson(String queryJson) { this.queryJson = queryJson; }
    public void setBody(String body) { this.body = body; }
    public void setAssertionsJson(String assertionsJson) { this.assertionsJson = assertionsJson; }
    public void setDatasetJson(String datasetJson) { this.datasetJson = datasetJson; }
    public void setInputContractJson(String inputContractJson) { this.inputContractJson = inputContractJson; }
    public void setOutputContractJson(String outputContractJson) { this.outputContractJson = outputContractJson; }
    public void setTimeoutSec(int timeoutSec) { this.timeoutSec = timeoutSec; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
