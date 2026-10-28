package com.testhub.uiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One performance sample for a device (CPU/memory/battery). */
@Entity
@Table(name = "ue_device_metric")
public class DeviceMetricEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "cpu_percent")
    private Double cpuPercent;

    @Column(name = "mem_used_percent")
    private Double memUsedPercent;

    @Column(name = "mem_used_kb")
    private Long memUsedKb;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "battery_temperature")
    private Double batteryTemperature;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeviceMetricEntity() {
    }

    public DeviceMetricEntity(String id, String deviceId, Double cpuPercent, Double memUsedPercent,
                              Long memUsedKb, Integer batteryLevel, Double batteryTemperature, Instant createdAt) {
        this.id = id;
        this.deviceId = deviceId;
        this.cpuPercent = cpuPercent;
        this.memUsedPercent = memUsedPercent;
        this.memUsedKb = memUsedKb;
        this.batteryLevel = batteryLevel;
        this.batteryTemperature = batteryTemperature;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public Double getCpuPercent() { return cpuPercent; }
    public Double getMemUsedPercent() { return memUsedPercent; }
    public Long getMemUsedKb() { return memUsedKb; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public Double getBatteryTemperature() { return batteryTemperature; }
    public Instant getCreatedAt() { return createdAt; }
}
