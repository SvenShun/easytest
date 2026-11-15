package com.easytest.core.feature.apiagent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Shared, database-backed API agent registration (multi-core safe). */
@Entity
@Table(name = "api_agent")
public class ApiAgentEntity {

    @Id
    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "base_url", length = 500, nullable = false)
    private String baseUrl;

    @Column(name = "capabilities_json", length = 1000)
    private String capabilitiesJson;

    @Column(name = "slots", nullable = false)
    private int slots = 1;

    @Column(name = "active_tasks", nullable = false)
    private int activeTasks;

    @Column(name = "status", length = 16, nullable = false)
    private String status = "UP";

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApiAgentEntity() {
    }

    public ApiAgentEntity(String agentId, String name, String baseUrl, String capabilitiesJson,
                          int slots, int activeTasks, String status, Instant lastSeenAt, Instant updatedAt) {
        this.agentId = agentId;
        this.name = name;
        this.baseUrl = baseUrl;
        this.capabilitiesJson = capabilitiesJson;
        this.slots = slots;
        this.activeTasks = activeTasks;
        this.status = status;
        this.lastSeenAt = lastSeenAt;
        this.updatedAt = updatedAt;
    }

    public String getAgentId() { return agentId; }
    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getCapabilitiesJson() { return capabilitiesJson; }
    public int getSlots() { return slots; }
    public int getActiveTasks() { return activeTasks; }
    public String getStatus() { return status; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setCapabilitiesJson(String capabilitiesJson) { this.capabilitiesJson = capabilitiesJson; }
    public void setSlots(int slots) { this.slots = slots; }
    public void setActiveTasks(int activeTasks) { this.activeTasks = activeTasks; }
    public void setStatus(String status) { this.status = status; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
