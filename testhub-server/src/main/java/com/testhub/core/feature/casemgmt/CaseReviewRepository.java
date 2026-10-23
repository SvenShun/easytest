package com.testhub.core.feature.casemgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseReviewRepository extends JpaRepository<CaseReview, String> {

    List<CaseReview> findByCaseIdOrderByCreatedAtAsc(String caseId);

    void deleteByCaseId(String caseId);
}
