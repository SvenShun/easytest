package com.testhub.core.feature.casemgmt;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Client-side functional case expressed as BDD, optionally bound to a script. */
@Entity
@Table(name = "client_case")
public class ClientCase {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "bdd_given", length = 4000)
    private String bddGiven;

    @Column(name = "bdd_when", length = 4000)
    private String bddWhen;

    @Column(name = "bdd_then", length = 4000)
    private String bddThen;

    @Column(name = "requirement_id", length = 64)
    private String requirementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 16, nullable = false)
    private ReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16, nullable = false)
    private CaseSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 16)
    private ExecutionMode executionMode = ExecutionMode.AI;

    @OneToMany(mappedBy = "clientCase", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("seq ASC")
    private List<CaseStep> steps = new ArrayList<>();

    @Column(name = "model_id", length = 128)
    private String modelId;

    @Column(name = "script_id", length = 64)
    private String scriptId;

    @Lob
    @Column(name = "failure_analysis")
    private String failureAnalysis;

    @Column(name = "failure_report_url", length = 1024)
    private String failureReportUrl;

    @Column(name = "failure_task_id", length = 64)
    private String failureTaskId;

    @Column(name = "failure_updated_at")
    private Instant failureUpdatedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ClientCase() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(String requirementId) {
        this.requirementId = requirementId;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public CaseSource getSource() {
        return source;
    }

    public void setSource(CaseSource source) {
        this.source = source;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode == null ? ExecutionMode.AI : executionMode;
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    public List<CaseStep> getSteps() {
        return steps;
    }

    public void setSteps(List<CaseStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getScriptId() {
        return scriptId;
    }

    public void setScriptId(String scriptId) {
        this.scriptId = scriptId;
    }

    public String getFailureAnalysis() {
        return failureAnalysis;
    }

    public void setFailureAnalysis(String failureAnalysis) {
        this.failureAnalysis = failureAnalysis;
    }

    public String getFailureReportUrl() {
        return failureReportUrl;
    }

    public void setFailureReportUrl(String failureReportUrl) {
        this.failureReportUrl = failureReportUrl;
    }

    public String getFailureTaskId() {
        return failureTaskId;
    }

    public void setFailureTaskId(String failureTaskId) {
        this.failureTaskId = failureTaskId;
    }

    public Instant getFailureUpdatedAt() {
        return failureUpdatedAt;
    }

    public void setFailureUpdatedAt(Instant failureUpdatedAt) {
        this.failureUpdatedAt = failureUpdatedAt;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
