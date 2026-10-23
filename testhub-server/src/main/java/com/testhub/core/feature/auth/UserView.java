package com.testhub.core.feature.auth;

import com.testhub.common.security.Role;

import java.time.Instant;

/** Safe user projection (never exposes the password hash). */
public record UserView(String id, String username, String displayName, Role role, boolean enabled,
                       Instant createdAt) {

    public static UserView of(AppUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                user.isEnabled(), user.getCreatedAt());
    }
}
