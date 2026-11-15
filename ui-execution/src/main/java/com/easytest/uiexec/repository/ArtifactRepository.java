package com.easytest.uiexec.repository;

import com.easytest.uiexec.domain.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtifactRepository extends JpaRepository<ArtifactEntity, String> {

    List<ArtifactEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);

    Optional<ArtifactEntity> findByTaskIdAndName(String taskId, String name);
}
