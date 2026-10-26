package com.testhub.uiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A device sub-server (agent) registered with ui-execution. Names are unique. */
@Entity
@Table(name = "ue_agent")
public class AgentEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", nullable = false, unique = true, length = 200)
    private String name;

    @Column(name = "host", length = 200)
    private String host;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    /** Disabled agents stay registered but their devices are offline and not schedulable. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentEntity() {
    }

    public AgentEntity(String id, String name, String host, String baseUrl, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.baseUrl = baseUrl;
        this.createdAt = createdAt;
        this.lastHeartbeat = createdAt;
        this.enabled = true;
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

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
