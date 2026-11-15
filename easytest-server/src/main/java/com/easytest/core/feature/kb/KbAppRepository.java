package com.easytest.core.feature.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface KbAppRepository extends JpaRepository<KbApp, String> {
    List<KbApp> findAllByOrderByUpdatedAtDesc();
    List<KbApp> findByProjectIdOrderByUpdatedAtDesc(String projectId);
    Optional<KbApp> findByName(String name);
}
