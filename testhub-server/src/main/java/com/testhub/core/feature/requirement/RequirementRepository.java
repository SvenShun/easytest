package com.testhub.core.feature.requirement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RequirementRepository extends JpaRepository<Requirement, String>,
        JpaSpecificationExecutor<Requirement> {
}
