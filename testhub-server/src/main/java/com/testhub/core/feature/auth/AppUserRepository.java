package com.testhub.core.feature.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import com.testhub.common.security.Role;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRoleAndEnabledTrue(Role role);
}
