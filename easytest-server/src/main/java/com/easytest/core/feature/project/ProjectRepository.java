package com.easytest.core.feature.project;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, String> {
    boolean existsByKey(String key);
}
