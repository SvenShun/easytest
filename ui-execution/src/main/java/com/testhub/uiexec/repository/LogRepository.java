package com.testhub.uiexec.repository;

import com.testhub.uiexec.domain.LogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LogRepository extends JpaRepository<LogEntity, String> {

    Optional<LogEntity> findFirstByTaskIdOrderBySeqDesc(String taskId);

    @Query("select coalesce(max(l.seq), 0) from LogEntity l where l.taskId = :taskId")
    int maxSeq(@Param("taskId") String taskId);

    List<LogEntity> findByTaskIdOrderBySeqDesc(String taskId, Pageable pageable);

    List<LogEntity> findByTaskIdAndSeqGreaterThanOrderBySeqAsc(String taskId, int seq, Pageable pageable);
}
