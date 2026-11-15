package com.easytest.core.feature.apiscenario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.List;

public interface ApiScenarioRunRepository extends JpaRepository<ApiScenarioRun, String> {
    Page<ApiScenarioRun> findByScenarioId(String scenarioId, Pageable pageable);

    java.util.Optional<ApiScenarioRun> findTopByScenarioIdOrderByCreatedAtDesc(String scenarioId);

    List<ApiScenarioRun> findByStatusIn(Collection<String> statuses);
}
