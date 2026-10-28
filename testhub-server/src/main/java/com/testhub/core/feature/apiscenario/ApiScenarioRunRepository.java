package com.testhub.core.feature.apiscenario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ApiScenarioRunRepository extends JpaRepository<ApiScenarioRun, String> {
    Page<ApiScenarioRun> findByScenarioId(String scenarioId, Pageable pageable);
}
