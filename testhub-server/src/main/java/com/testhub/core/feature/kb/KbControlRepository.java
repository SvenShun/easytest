package com.testhub.core.feature.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface KbControlRepository extends JpaRepository<KbControl, String> {
    List<KbControl> findByPageIdOrderByUpdatedAtDesc(String pageId);
    long countByPageId(String pageId);
    void deleteByPageId(String pageId);
}
