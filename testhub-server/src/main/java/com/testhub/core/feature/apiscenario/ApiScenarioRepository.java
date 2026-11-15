package com.testhub.core.feature.apiscenario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ApiScenarioRepository extends JpaRepository<ApiScenario, String>,
        JpaSpecificationExecutor<ApiScenario> {

    Optional<ApiScenario> findBySourcePath(String sourcePath);
}
