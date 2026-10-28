package com.testhub.core.feature.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A named collection of approved client cases. */
@Entity
@Table(name = "test_plan")
public class TestPlan {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Comma separated devices bound to this plan (matrix defaults). */
    @Column(name = "device_ids", length = 2000)
    private String deviceIds;

    /** Optional API environment (base URL, variables, auth preset). */
    @Column(name = "api_environment_id", length = 64)
    private String environmentId;

    /** MeterSphere test plan id used for execution-result write-back (optional). */
    @Column(name = "metersphere_test_plan_id", length = 64)
    private String metersphereTestPlanId;

    protected TestPlan() {
    }

    public TestPlan(String id, String name, String description, String status, String createdBy,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDeviceIds() { return deviceIds; }
    public void setDeviceIds(String deviceIds) { this.deviceIds = deviceIds; }

    public String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }

    public String getMetersphereTestPlanId() {
        return metersphereTestPlanId;
    }

    public void setMetersphereTestPlanId(String metersphereTestPlanId) {
        this.metersphereTestPlanId = metersphereTestPlanId;
    }
}
