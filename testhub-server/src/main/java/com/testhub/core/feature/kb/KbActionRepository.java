package com.testhub.core.feature.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface KbActionRepository extends JpaRepository<KbAction, String> {
    List<KbAction> findByControlIdOrderByCreatedAtAsc(String controlId);
    long countByControlId(String controlId);
    void deleteByControlId(String controlId);
}
