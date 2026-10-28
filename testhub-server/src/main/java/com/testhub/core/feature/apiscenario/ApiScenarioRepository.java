package com.testhub.core.feature.apiscenario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApiScenarioRepository extends JpaRepository<ApiScenario, String>,
        JpaSpecificationExecutor<ApiScenario> {
}
