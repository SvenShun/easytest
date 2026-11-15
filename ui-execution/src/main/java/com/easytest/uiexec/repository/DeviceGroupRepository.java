package com.easytest.uiexec.repository;

import com.easytest.uiexec.domain.DeviceGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceGroupRepository extends JpaRepository<DeviceGroupEntity, String> {
    List<DeviceGroupEntity> findAllByOrderByNameAsc();
    Optional<DeviceGroupEntity> findByName(String name);
}
