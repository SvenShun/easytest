package com.testhub.apiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One API execution task: the request snapshot, the captured HTTP response,
 * parsed assertion results and the rendered report. Request/response metadata
 * is stored as JSON text so the schema stays portable between H2 and MySQL.
 */
@Entity
@Table(name = "api_task", indexes = {
        @Index(name = "idx_api_task_status", columnList = "status"),
        @Index(name = "idx_api_task_plan_run", columnList = "plan_run_id"),
        @Index(name = "idx_api_task_case", columnList = "case_id"),
        @Index(name = "idx_api_task_created", columnList = "created_at")
})
public class ApiTaskEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "plan_id", length = 100)
    private String planId;

    @Column(name = "plan_run_id", length = 128)
    private String planRunId;

    @Column(name = "case_id", nullable = false, length = 128)
    private String caseId;

    @Column(name = "case_name", length = 500)
    private String caseName;

    @Column(name = "method", nullable = false, length = 16)
    private String method;

    /** Fully resolved request URL (baseUrl + path + query). */
    @Column(name = "url", nullable = false, length = 4000)
    private String url;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    @Column(name = "query_json", columnDefinition = "TEXT")
    private String queryJson;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "assertions_json", columnDefinition = "TEXT")
    private String assertionsJson;

    /** Serialized {@code List<AssertionResult>} produced by the execution. */
    @Column(name = "assertion_results_json", columnDefinition = "TEXT")
    private String assertionResultsJson;

    @Column(name = "timeout_sec", nullable = false)
    private int timeoutSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ApiTaskStatus status;

    /** Instance and worker index that claimed the task (e.g. host-a1b2-1). */
    @Column(name = "worker_id", length = 128)
    private String workerId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "response_headers", columnDefinition = "TEXT")
    private String responseHeaders;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "failure_reason", length = 40)
    private String failureReason;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    protected ApiTaskEntity() {
    }

    public ApiTaskEntity(String id, String caseId, String method, String url, ApiTaskStatus status,
                         int timeoutSec, Instant createdAt) {
        this.id = id;
        this.caseId = caseId;
        this.method = method;
        this.url = url;
        this.status = status;
        this.timeoutSec = timeoutSec;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlanRunId() {
        return planRunId;
    }

    public void setPlanRunId(String planRunId) {
        this.planRunId = planRunId;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public void setHeadersJson(String headersJson) {
        this.headersJson = headersJson;
    }

    public String getQueryJson() {
        return queryJson;
    }

    public void setQueryJson(String queryJson) {
        this.queryJson = queryJson;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getAssertionsJson() {
        return assertionsJson;
    }

    public void setAssertionsJson(String assertionsJson) {
        this.assertionsJson = assertionsJson;
    }

    public String getAssertionResultsJson() {
        return assertionResultsJson;
    }

    public void setAssertionResultsJson(String assertionResultsJson) {
        this.assertionResultsJson = assertionResultsJson;
    }

    public int getTimeoutSec() {
        return timeoutSec;
    }

    public void setTimeoutSec(int timeoutSec) {
        this.timeoutSec = timeoutSec;
    }

    public ApiTaskStatus getStatus() {
        return status;
    }

    public void setStatus(ApiTaskStatus status) {
        this.status = status;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(String responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Instant getCancelRequestedAt() {
        return cancelRequestedAt;
    }

    public void setCancelRequestedAt(Instant cancelRequestedAt) {
        this.cancelRequestedAt = cancelRequestedAt;
    }
}
