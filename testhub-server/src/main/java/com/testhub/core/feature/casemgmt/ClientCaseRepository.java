package com.testhub.core.feature.casemgmt;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ClientCaseRepository extends JpaRepository<ClientCase, String>,
        JpaSpecificationExecutor<ClientCase> {

    List<ClientCase> findByReviewStatusOrderByUpdatedAtDesc(ReviewStatus reviewStatus, Pageable pageable);

    long countByReviewStatus(ReviewStatus reviewStatus);
}
