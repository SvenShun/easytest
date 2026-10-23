package com.testhub.uiexec.repository;

import com.testhub.uiexec.domain.StepEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StepRepository extends JpaRepository<StepEntity, String> {

    Optional<StepEntity> findByTaskIdAndSeq(String taskId, int seq);

    List<StepEntity> findByTaskIdOrderBySeqAsc(String taskId);

    List<StepEntity> findByTaskIdOrderBySeqAsc(String taskId, Pageable pageable);

    List<StepEntity> findByTaskIdAndSeqGreaterThanOrderBySeqAsc(String taskId, int seq, Pageable pageable);

    long countByTaskId(String taskId);
}
