package com.testhub.core.feature.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface TestJobRepository extends JpaRepository<TestJob, String>, JpaSpecificationExecutor<TestJob> {
    List<TestJob> findByEnabledTrueAndNextRunAtLessThanEqual(Instant now);
}
