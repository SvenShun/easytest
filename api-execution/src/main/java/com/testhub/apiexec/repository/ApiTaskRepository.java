package com.testhub.apiexec.repository;

import com.testhub.apiexec.domain.ApiTaskEntity;
import com.testhub.apiexec.domain.ApiTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface ApiTaskRepository extends JpaRepository<ApiTaskEntity, String>,
        JpaSpecificationExecutor<ApiTaskEntity> {

    long countByStatus(ApiTaskStatus status);

    List<ApiTaskEntity> findByStatusIn(Collection<ApiTaskStatus> statuses);
}
