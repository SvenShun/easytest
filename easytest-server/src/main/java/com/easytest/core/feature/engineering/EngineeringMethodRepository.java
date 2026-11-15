package com.easytest.core.feature.engineering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EngineeringMethodRepository extends JpaRepository<EngineeringMethod, String> {
    Optional<EngineeringMethod> findByMethodPath(String methodPath);

    boolean existsByMethodPath(String methodPath);
}
