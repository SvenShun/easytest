package com.testhub.apiexec.scenario;

import com.testhub.apiexec.domain.ApiTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface ScenarioTaskRepository extends JpaRepository<ScenarioTaskEntity, String> {

    List<ScenarioTaskEntity> findByStatus(ApiTaskStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ScenarioTaskEntity t set t.status = :running, t.workerId = :workerId, "
            + "t.startedAt = :startedAt where t.id = :id and t.status = :pending")
    int claim(@Param("id") String id, @Param("running") ApiTaskStatus running,
              @Param("pending") ApiTaskStatus pending, @Param("workerId") String workerId,
              @Param("startedAt") Instant startedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ScenarioTaskEntity t set t.status = :to, t.failureReason = :reason, "
            + "t.summary = :summary, t.finishedAt = :finishedAt where t.id = :id and t.status = :from")
    int transition(@Param("id") String id, @Param("from") ApiTaskStatus from,
                   @Param("to") ApiTaskStatus to, @Param("reason") String reason,
                   @Param("summary") String summary, @Param("finishedAt") Instant finishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ScenarioTaskEntity t set t.status = :cancelled, t.failureReason = 'CANCELLED', "
            + "t.finishedAt = :finishedAt, t.cancelRequestedAt = :now "
            + "where t.id = :id and (t.status = :pending or t.status = :running)")
    int cancelActive(@Param("id") String id, @Param("cancelled") ApiTaskStatus cancelled,
                     @Param("pending") ApiTaskStatus pending, @Param("running") ApiTaskStatus running,
                     @Param("finishedAt") Instant finishedAt, @Param("now") Instant now);
}
