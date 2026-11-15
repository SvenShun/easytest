package com.easytest.core.feature.apiagent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ApiAgentRepository extends JpaRepository<ApiAgentEntity, String> {
    List<ApiAgentEntity> findByStatusAndLastSeenAtAfterOrderByActiveTasksAsc(String status, Instant seenAfter);

    @Modifying
    @Query("update ApiAgentEntity a set a.activeTasks = a.activeTasks + 1 "
            + "where a.agentId = :id and a.status = 'UP' and a.activeTasks < a.slots")
    int reserve(@Param("id") String id);

    @Modifying
    @Query("update ApiAgentEntity a set a.activeTasks = case when a.activeTasks > 0 then a.activeTasks - 1 else 0 end "
            + "where a.agentId = :id")
    int release(@Param("id") String id);

    @Modifying
    @Query("update ApiAgentEntity a set a.status = 'DOWN', a.updatedAt = :now "
            + "where a.status = 'UP' and a.lastSeenAt < :cutoff")
    int markStaleDown(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Modifying
    @Query("update ApiAgentEntity a set a.status = :status, a.updatedAt = :now where a.agentId = :id")
    int updateStatus(@Param("id") String id, @Param("status") String status, @Param("now") Instant now);
}
