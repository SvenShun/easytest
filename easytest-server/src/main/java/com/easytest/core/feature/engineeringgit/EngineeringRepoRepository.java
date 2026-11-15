package com.easytest.core.feature.engineeringgit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EngineeringRepoRepository extends JpaRepository<EngineeringRepo, String> {
    List<EngineeringRepo> findByProjectIdOrderByUpdatedAtDesc(String projectId);
    List<EngineeringRepo> findByEnabledTrue();
}
