package com.testhub.uiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** A physical / simulated device owned by an agent. Unique per (agentId, serial). */
@Entity
@Table(name = "ue_device", uniqueConstraints = @UniqueConstraint(
        name = "uk_ue_device_agent_serial", columnNames = {"agent_id", "serial"}))
public class DeviceEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "serial", nullable = false, length = 200)
    private String serial;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "model", length = 200)
    private String model;

    @Column(name = "platform", length = 50)
    private String platform;

    /** Comma separated tag list, e.g. {@code android,pixel}. */
    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "online", nullable = false)
    private boolean online;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected DeviceEntity() {
    }

    public DeviceEntity(String id, String agentId, String serial) {
        this.id = id;
        this.agentId = agentId;
        this.serial = serial;
        this.online = true;
        this.lastSeenAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
