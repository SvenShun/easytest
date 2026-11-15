package com.easytest.core.feature.apiagent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** Durable queue entry for an API task waiting for an agent slot. */
@Entity
@Table(name = "api_dispatch_task")
public class ApiDispatchTask {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "plan_run_id", length = 64, nullable = false)
    private String planRunId;

    @Column(name = "plan_run_item_id", length = 64, nullable = false)
    private String planRunItemId;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "status", length = 16, nullable = false)
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "claimed_by", length = 64)
    private String claimedBy;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApiDispatchTask() {
    }

    public ApiDispatchTask(String id, String planRunId, String planRunItemId, String payloadJson,
                           Instant createdAt) {
        this.id = id;
        this.planRunId = planRunId;
        this.planRunItemId = planRunItemId;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String getId() { return id; }
    public String getPlanRunId() { return planRunId; }
    public String getPlanRunItemId() { return planRunItemId; }
    public String getPayloadJson() { return payloadJson; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getClaimedBy() { return claimedBy; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
