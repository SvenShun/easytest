package com.testhub.core.feature.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Mounted case inside a plan, with an optional executor override. */
@Entity
@Table(name = "plan_case", uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "case_id"}))
public class PlanCase {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "plan_id", length = 64, nullable = false)
    private String planId;

    @Column(name = "case_id", length = 64, nullable = false)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", length = 16)
    private CaseType caseType = CaseType.UI;

    @Enumerated(EnumType.STRING)
    @Column(name = "executor_type_override", length = 16)
    private ExecutorType executorTypeOverride;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** MeterSphere functional case id (optional; used only for result write-back). */
    @Column(name = "metersphere_case_id", length = 64)
    private String metersphereCaseId;

    /** MeterSphere test-plan functional case row id, required to post an execution result. */
    @Column(name = "metersphere_test_plan_case_id", length = 64)
    private String metersphereTestPlanCaseId;

    protected PlanCase() {
    }

    public PlanCase(String id, String planId, String caseId, ExecutorType executorTypeOverride,
                    Instant createdAt) {
        this(id, planId, caseId, CaseType.UI, executorTypeOverride, createdAt);
    }

    public PlanCase(String id, String planId, String caseId, CaseType caseType,
                    ExecutorType executorTypeOverride, Instant createdAt) {
        this.id = id;
        this.planId = planId;
        this.caseId = caseId;
        this.caseType = caseType;
        this.executorTypeOverride = executorTypeOverride;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getPlanId() {
        return planId;
    }

    public String getCaseId() {
        return caseId;
    }

    public CaseType getCaseType() {
        return caseType == null ? CaseType.UI : caseType;
    }

    public void setCaseType(CaseType caseType) {
        this.caseType = caseType;
    }

    public ExecutorType getExecutorTypeOverride() {
        return executorTypeOverride;
    }

    public void setExecutorTypeOverride(ExecutorType executorTypeOverride) {
        this.executorTypeOverride = executorTypeOverride;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getMetersphereCaseId() {
        return metersphereCaseId;
    }

    public void setMetersphereCaseId(String metersphereCaseId) {
        this.metersphereCaseId = metersphereCaseId;
    }

    public String getMetersphereTestPlanCaseId() {
        return metersphereTestPlanCaseId;
    }

    public void setMetersphereTestPlanCaseId(String metersphereTestPlanCaseId) {
        this.metersphereTestPlanCaseId = metersphereTestPlanCaseId;
    }
}
