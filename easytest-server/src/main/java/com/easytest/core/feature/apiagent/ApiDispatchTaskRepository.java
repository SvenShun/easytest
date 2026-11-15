package com.easytest.core.feature.apiagent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiDispatchTaskRepository extends JpaRepository<ApiDispatchTask, String> {
    List<ApiDispatchTask> findTop20ByStatusOrderByCreatedAtAsc(String status);
}
