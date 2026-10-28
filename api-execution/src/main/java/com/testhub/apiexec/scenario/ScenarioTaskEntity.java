package com.testhub.apiexec.scenario;

import com.testhub.apiexec.domain.ApiTaskStatus;
import jakarta.persistence.*;
import java.time.Instant;

/** Queued execution of one YAML API scenario. */
@Entity
@Table(name = "api_scenario_task", indexes = {
        @Index(name = "idx_scenario_task_status", columnList = "status"),
        @Index(name = "idx_scenario_task_run", columnList = "run_id")
})
public class ScenarioTaskEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "scenario_id", nullable = false, length = 128)
    private String scenarioId;

    @Column(name = "run_id", length = 128)
    private String runId;

    @Column(name = "scenario_name", length = 500)
    private String scenarioName;

    @Column(name = "yaml_text", columnDefinition = "TEXT", nullable = false)
    private String yamlText;

    @Column(name = "environment_json", columnDefinition = "TEXT")
    private String environmentJson;

    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ApiTaskStatus status;

    @Column(name = "worker_id", length = 128)
    private String workerId;

    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson;

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "failure_reason", length = 40)
    private String failureReason;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "timeout_sec", nullable = false)
    private int timeoutSec = 120;

    @Column(name = "plan_id", length = 100)
    private String planId;

    @Column(name = "plan_run_id", length = 128)
    private String planRunId;

    @Column(name = "case_id", length = 128)
    private String caseId;

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

    protected ScenarioTaskEntity() {
    }

    public ScenarioTaskEntity(String id, String scenarioId, String runId, String scenarioName, String yamlText,
                              String environmentJson, String variablesJson, int timeoutSec, Instant createdAt) {
        this.id = id;
        this.scenarioId = scenarioId;
        this.runId = runId;
        this.scenarioName = scenarioName;
        this.yamlText = yamlText;
        this.environmentJson = environmentJson;
        this.variablesJson = variablesJson;
        this.timeoutSec = timeoutSec;
        this.status = ApiTaskStatus.PENDING;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getScenarioId() { return scenarioId; }
    public String getRunId() { return runId; }
    public String getScenarioName() { return scenarioName; }
    public String getYamlText() { return yamlText; }
    public String getEnvironmentJson() { return environmentJson; }
    public String getVariablesJson() { return variablesJson; }
    public ApiTaskStatus getStatus() { return status; }
    public String getWorkerId() { return workerId; }
    public String getStepsJson() { return stepsJson; }
    public String getReportJson() { return reportJson; }
    public String getSummary() { return summary; }
    public String getFailureReason() { return failureReason; }
    public Long getDurationMs() { return durationMs; }
    public int getTimeoutSec() { return timeoutSec; }
    public String getPlanId() { return planId; }
    public String getPlanRunId() { return planRunId; }
    public String getCaseId() { return caseId; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCancelRequestedAt() { return cancelRequestedAt; }

    public void setStatus(ApiTaskStatus status) { this.status = status; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }
    public void setReportJson(String reportJson) { this.reportJson = reportJson; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public void setPlanId(String planId) { this.planId = planId; }
    public void setPlanRunId(String planRunId) { this.planRunId = planRunId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public void setCancelRequestedAt(Instant cancelRequestedAt) { this.cancelRequestedAt = cancelRequestedAt; }
}
