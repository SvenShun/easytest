package com.testhub.core.feature.apiscenario;

import jakarta.persistence.*;
import java.time.Instant;

/** One execution history entry for an API scenario. */
@Entity
@Table(name = "api_scenario_run", indexes = {
        @Index(name = "idx_scenario_run_scenario", columnList = "scenario_id"),
        @Index(name = "idx_scenario_run_status", columnList = "status")
})
public class ApiScenarioRun {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "scenario_id", nullable = false, length = 64)
    private String scenarioId;

    @Column(name = "scenario_name", length = 255)
    private String scenarioName;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "environment_id", length = 64)
    private String environmentId;

    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;

    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson;

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ApiScenarioRun() {
    }

    public ApiScenarioRun(String id, String scenarioId, String scenarioName, String environmentId,
                          String variablesJson, String createdBy, Instant createdAt) {
        this.id = id;
        this.scenarioId = scenarioId;
        this.scenarioName = scenarioName;
        this.environmentId = environmentId;
        this.variablesJson = variablesJson;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getScenarioId() { return scenarioId; }
    public String getScenarioName() { return scenarioName; }
    public String getTaskId() { return taskId; }
    public String getStatus() { return status; }
    public String getEnvironmentId() { return environmentId; }
    public String getVariablesJson() { return variablesJson; }
    public String getStepsJson() { return stepsJson; }
    public String getReportJson() { return reportJson; }
    public String getSummary() { return summary; }
    public String getFailureReason() { return failureReason; }
    public Long getDurationMs() { return durationMs; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }

    public void setTaskId(String taskId) { this.taskId = taskId; }
    public void setStatus(String status) { this.status = status; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }
    public void setReportJson(String reportJson) { this.reportJson = reportJson; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
