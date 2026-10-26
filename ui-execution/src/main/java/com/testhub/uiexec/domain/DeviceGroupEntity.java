package com.testhub.uiexec.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** A user-defined device group (Sonic agent/machine grouping replacement). */
@Entity
@Table(name = "ue_device_group", uniqueConstraints = @UniqueConstraint(
        name = "uk_ue_device_group_name", columnNames = "name"))
public class DeviceGroupEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ue_device_group_member", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "device_id", length = 64)
    private Set<String> deviceIds = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceGroupEntity() {
    }

    public DeviceGroupEntity(String id, String name, String description, Set<String> deviceIds,
                             Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.deviceIds = deviceIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(deviceIds);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Set<String> getDeviceIds() { return deviceIds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setDeviceIds(Set<String> deviceIds) {
        this.deviceIds.clear();
        if (deviceIds != null) {
            this.deviceIds.addAll(deviceIds);
        }
    }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
