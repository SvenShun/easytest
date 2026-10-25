package com.testhub.apiexec.repository;

import com.testhub.apiexec.domain.ApiTaskEntity;
import com.testhub.apiexec.domain.ApiTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface ApiTaskRepository extends JpaRepository<ApiTaskEntity, String>,
        JpaSpecificationExecutor<ApiTaskEntity> {

    long countByStatus(ApiTaskStatus status);

    List<ApiTaskEntity> findByStatus(ApiTaskStatus status);

    /**
     * Atomic terminal transition; returns 0 when another instance already
     * moved the task out of {@code from}, so a terminal state is never
     * overwritten.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ApiTaskEntity t set t.status = :to, t.failureReason = :failureReason, "
            + "t.summary = :summary, t.finishedAt = :finishedAt "
            + "where t.id = :id and t.status = :from")
    int transition(@Param("id") String id,
                   @Param("from") ApiTaskStatus from,
                   @Param("to") ApiTaskStatus to,
                   @Param("failureReason") String failureReason,
                   @Param("summary") String summary,
                   @Param("finishedAt") Instant finishedAt);

    /** Atomically claims a PENDING task and records the winning worker. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ApiTaskEntity t set t.status = :running, t.workerId = :workerId, "
            + "t.startedAt = :startedAt where t.id = :id and t.status = :pending")
    int claim(@Param("id") String id,
              @Param("running") ApiTaskStatus running,
              @Param("pending") ApiTaskStatus pending,
              @Param("workerId") String workerId,
              @Param("startedAt") Instant startedAt);

    /** Cancels a queued or running task; terminal tasks are left untouched. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ApiTaskEntity t set t.status = :cancelled, t.failureReason = :failureReason, "
            + "t.finishedAt = :finishedAt, t.cancelRequestedAt = :cancelRequestedAt "
            + "where t.id = :id and (t.status = :pending or t.status = :running)")
    int cancelActive(@Param("id") String id,
                     @Param("cancelled") ApiTaskStatus cancelled,
                     @Param("pending") ApiTaskStatus pending,
                     @Param("running") ApiTaskStatus running,
                     @Param("failureReason") String failureReason,
                     @Param("finishedAt") Instant finishedAt,
                     @Param("cancelRequestedAt") Instant cancelRequestedAt);
}
