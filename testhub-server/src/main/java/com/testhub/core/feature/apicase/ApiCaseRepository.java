package com.testhub.core.feature.apicase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApiCaseRepository extends JpaRepository<ApiCase, String>, JpaSpecificationExecutor<ApiCase> {
}
