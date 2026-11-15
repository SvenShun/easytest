package com.easytest.core.feature.apiagent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ApiDispatchTaskRepository extends JpaRepository<ApiDispatchTask, String> {
    @Query("select t from ApiDispatchTask t where t.status = 'PENDING' "
            + "or (t.status = 'CLAIMED' and (t.leaseUntil is null or t.leaseUntil < :now)) "
            + "order by t.createdAt asc")
    List<ApiDispatchTask> findClaimable(@Param("now") Instant now);

    @Modifying
    @Query("update ApiDispatchTask t set t.status='CLAIMED', t.claimedBy=:owner, t.leaseUntil=:leaseUntil, "
            + "t.updatedAt=:now where t.id=:id and (t.status='PENDING' "
            + "or (t.status='CLAIMED' and (t.leaseUntil is null or t.leaseUntil < :now)))")
    int claim(@Param("id") String id, @Param("owner") String owner,
              @Param("leaseUntil") Instant leaseUntil, @Param("now") Instant now);
}
