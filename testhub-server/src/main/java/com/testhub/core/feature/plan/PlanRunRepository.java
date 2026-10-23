package com.testhub.core.feature.plan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRunRepository extends JpaRepository<PlanRun, String> {

    Page<PlanRun> findByPlanId(String planId, Pageable pageable);

    List<PlanRun> findByPlanIdOrderByStartedAtDesc(String planId);

    List<PlanRun> findByPlanId(String planId);

    boolean existsByPlanIdAndStatus(String planId, RunStatus status);
}
