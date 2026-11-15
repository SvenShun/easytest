package com.easytest.uiexec.repository;

import com.easytest.uiexec.domain.FailureCategory;
import com.easytest.uiexec.domain.FailureReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FailureReportRepository extends JpaRepository<FailureReportEntity, String> {

    Optional<FailureReportEntity> findFirstByTaskIdOrderByCreatedAtDesc(String taskId);

    interface CategoryCount {
        FailureCategory getCategory();

        long getCount();
    }

    @Query("select f.reasonCategory as category, count(f) as count from FailureReportEntity f "
            + "where f.reasonCategory is not null group by f.reasonCategory order by count(f) desc")
    List<CategoryCount> countByCategory();
}
