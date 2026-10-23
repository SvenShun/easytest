package com.testhub.core.feature.system;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, String> {

    Optional<ModelConfig> findByConfigType(String configType);

    boolean existsByConfigType(String configType);
}
