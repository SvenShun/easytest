package com.testhub.uiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** One UI execution task (a case dispatched to a device). */
@Entity
@Table(name = "ue_task", indexes = {
        @Index(name = "idx_ue_task_status", columnList = "status"),
        @Index(name = "idx_ue_task_device", columnList = "device_id"),
        @Index(name = "idx_ue_task_plan_run", columnList = "plan_run_id"),
        @Index(name = "idx_ue_task_case", columnList = "case_id")
})
public class TaskEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "plan_id", length = 64)
    private String planId;

    @Column(name = "plan_run_id", length = 64)
    private String planRunId;

    @Column(name = "case_id", nullable = false, length = 64)
    private String caseId;

    @Column(name = "case_title", length = 500)
    private String caseTitle;

    @Column(name = "bdd_given", columnDefinition = "TEXT")
    private String bddGiven;

    @Column(name = "bdd_when", columnDefinition = "TEXT")
    private String bddWhen;

    @Column(name = "bdd_then", columnDefinition = "TEXT")
    private String bddThen;

    @Enumerated(EnumType.STRING)
    @Column(name = "executor_type", nullable = false, length = 32)
    private ExecutorType executorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TaskStatus status;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "device_serial", length = 200)
    private String deviceSerial;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "skills_repo", length = 500)
    private String skillsRepo;

    @Column(name = "skills_commit", length = 100)
    private String skillsCommit;

    /** Serialised {@link StepSpec} list for {@code STEP} tasks; null for AI/SCRIPT tasks. */
    @Lob
    @Column(name = "steps_json")
    private String stepsJson;

    @Column(name = "timeout_sec", nullable = false)
    private int timeoutSec;

    @Column(name = "step_timeout_sec", nullable = false)
    private int stepTimeoutSec;

    @Column(name = "queue_position", nullable = false)
    private int queuePosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private FailureCategory failureReason;

    @Column(name = "failure_summary", columnDefinition = "TEXT")
    private String failureSummary;

    @Column(name = "failure_report_url", length = 1000)
    private String failureReportUrl;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    protected TaskEntity() {
    }

    public TaskEntity(String id, String caseId, ExecutorType executorType, TaskStatus status,
                      int timeoutSec, int stepTimeoutSec, Instant createdAt) {
        this.id = id;
        this.caseId = caseId;
        this.executorType = executorType;
        this.status = status;
        this.timeoutSec = timeoutSec;
        this.stepTimeoutSec = stepTimeoutSec;
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

    public String getCaseTitle() {
        return caseTitle;
    }

    public void setCaseTitle(String caseTitle) {
        this.caseTitle = caseTitle;
    }

    public String getBddGiven() {
        return bddGiven;
    }

    public void setBddGiven(String bddGiven) {
        this.bddGiven = bddGiven;
    }

    public String getBddWhen() {
        return bddWhen;
    }

    public void setBddWhen(String bddWhen) {
        this.bddWhen = bddWhen;
    }

    public String getBddThen() {
        return bddThen;
    }

    public void setBddThen(String bddThen) {
        this.bddThen = bddThen;
    }

    public ExecutorType getExecutorType() {
        return executorType;
    }

    public void setExecutorType(ExecutorType executorType) {
        this.executorType = executorType;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceSerial() {
        return deviceSerial;
    }

    public void setDeviceSerial(String deviceSerial) {
        this.deviceSerial = deviceSerial;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getSkillsRepo() {
        return skillsRepo;
    }

    public void setSkillsRepo(String skillsRepo) {
        this.skillsRepo = skillsRepo;
    }

    public String getSkillsCommit() {
        return skillsCommit;
    }

    public void setSkillsCommit(String skillsCommit) {
        this.skillsCommit = skillsCommit;
    }

    public String getStepsJson() {
        return stepsJson;
    }

    public void setStepsJson(String stepsJson) {
        this.stepsJson = stepsJson;
    }

    public int getTimeoutSec() {
        return timeoutSec;
    }

    public void setTimeoutSec(int timeoutSec) {
        this.timeoutSec = timeoutSec;
    }

    public int getStepTimeoutSec() {
        return stepTimeoutSec;
    }

    public void setStepTimeoutSec(int stepTimeoutSec) {
        this.stepTimeoutSec = stepTimeoutSec;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(int queuePosition) {
        this.queuePosition = queuePosition;
    }

    public FailureCategory getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(FailureCategory failureReason) {
        this.failureReason = failureReason;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public void setFailureSummary(String failureSummary) {
        this.failureSummary = failureSummary;
    }

    public String getFailureReportUrl() {
        return failureReportUrl;
    }

    public void setFailureReportUrl(String failureReportUrl) {
        this.failureReportUrl = failureReportUrl;
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

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
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
