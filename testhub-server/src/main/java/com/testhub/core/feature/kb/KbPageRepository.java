package com.testhub.core.feature.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface KbPageRepository extends JpaRepository<KbPage, String> {
    List<KbPage> findByAppIdOrderByNameAsc(String appId);
    long countByAppId(String appId);
    void deleteByAppId(String appId);
}
