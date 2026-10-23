package com.testhub.uiexec.repository;

import com.testhub.uiexec.domain.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtifactRepository extends JpaRepository<ArtifactEntity, String> {

    List<ArtifactEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);

    Optional<ArtifactEntity> findByTaskIdAndName(String taskId, String name);
}
