package com.easytest.core.feature.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, String> {
    List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(String projectId);
    List<ProjectMember> findByUsernameOrderByCreatedAtAsc(String username);
    Optional<ProjectMember> findByProjectIdAndUsername(String projectId, String username);
    boolean existsByProjectIdAndUsername(String projectId, String username);
}
