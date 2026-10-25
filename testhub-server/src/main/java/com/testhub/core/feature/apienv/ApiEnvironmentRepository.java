package com.testhub.core.feature.apienv;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApiEnvironmentRepository extends JpaRepository<ApiEnvironment, String>,
        JpaSpecificationExecutor<ApiEnvironment> {
}
