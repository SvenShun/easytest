package com.testhub.core.feature.caselink;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CaseAutomationLinkRepository extends JpaRepository<CaseAutomationLink, String> {
    List<CaseAutomationLink> findByClientCaseIdOrderByCreatedAtDesc(String clientCaseId);
    Optional<CaseAutomationLink> findByIdAndClientCaseId(String id, String clientCaseId);
    boolean existsByClientCaseIdAndTargetTypeAndTargetId(String clientCaseId, String targetType, String targetId);
}
