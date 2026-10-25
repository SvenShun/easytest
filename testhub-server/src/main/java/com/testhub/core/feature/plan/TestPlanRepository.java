package com.testhub.core.feature.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestPlanRepository extends JpaRepository<TestPlan, String>,
        JpaSpecificationExecutor<TestPlan> {

    boolean existsByEnvironmentId(String environmentId);
}
