package com.testhub.uiexec.repository;

import com.testhub.uiexec.domain.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<DeviceEntity, String> {

    Optional<DeviceEntity> findByAgentIdAndSerial(String agentId, String serial);

    List<DeviceEntity> findByAgentId(String agentId);

    void deleteByAgentId(String agentId);
}
