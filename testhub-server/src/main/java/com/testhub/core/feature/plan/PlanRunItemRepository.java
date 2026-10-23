package com.testhub.core.feature.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRunItemRepository extends JpaRepository<PlanRunItem, String> {

    List<PlanRunItem> findByRunIdOrderByCreatedAtAscIdAsc(String runId);

    Optional<PlanRunItem> findByTaskId(String taskId);

    Optional<PlanRunItem> findByRunIdAndCaseId(String runId, String caseId);
}
