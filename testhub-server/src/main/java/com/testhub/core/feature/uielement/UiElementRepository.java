package com.testhub.core.feature.uielement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UiElementRepository extends JpaRepository<UiElement, String>, JpaSpecificationExecutor<UiElement> {
    Optional<UiElement> findByName(String name);
    boolean existsByName(String name);
}
