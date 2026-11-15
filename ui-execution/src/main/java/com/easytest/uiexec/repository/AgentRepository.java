package com.easytest.uiexec.repository;

import com.easytest.uiexec.domain.AgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentRepository extends JpaRepository<AgentEntity, String> {

    Optional<AgentEntity> findByName(String name);
}
