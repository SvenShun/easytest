package com.testhub.core.feature.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanCaseRepository extends JpaRepository<PlanCase, String> {

    List<PlanCase> findByPlanIdOrderByCreatedAtAscIdAsc(String planId);

    Optional<PlanCase> findByPlanIdAndCaseId(String planId, String caseId);

    boolean existsByPlanIdAndCaseId(String planId, String caseId);

    boolean existsByCaseId(String caseId);

    boolean existsByPlanIdAndCaseTypeAndCaseId(String planId, CaseType caseType, String caseId);

    void deleteByPlanId(String planId);
}
