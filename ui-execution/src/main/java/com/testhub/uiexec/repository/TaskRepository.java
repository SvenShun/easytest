package com.testhub.uiexec.repository;

import com.testhub.uiexec.domain.TaskEntity;
import com.testhub.uiexec.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, String>, JpaSpecificationExecutor<TaskEntity> {

    Optional<TaskEntity> findFirstByDeviceIdAndStatusInOrderByDispatchedAtDesc(String deviceId,
                                                                               Collection<TaskStatus> statuses);

    List<TaskEntity> findByDeviceIdAndStatusIn(String deviceId, Collection<TaskStatus> statuses);

    List<TaskEntity> findByStatusIn(Collection<TaskStatus> statuses);

    List<TaskEntity> findByStatusAndAgentIdIsNotNull(TaskStatus status);

    long countByStatus(TaskStatus status);

    long countByStatusIn(Collection<TaskStatus> statuses);

    List<TaskEntity> findByPlanRunIdOrderByCreatedAtAsc(String planRunId);
}
