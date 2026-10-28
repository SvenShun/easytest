package com.testhub.uiexec.repository;

import com.testhub.uiexec.domain.DeviceMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DeviceMetricRepository extends JpaRepository<DeviceMetricEntity, String> {
    List<DeviceMetricEntity> findByDeviceIdAndCreatedAtAfterOrderByCreatedAtAsc(String deviceId, Instant after);
    List<DeviceMetricEntity> findTop100ByDeviceIdOrderByCreatedAtDesc(String deviceId);
}
