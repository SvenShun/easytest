package com.testhub.common.security;

/**
 * Authenticated caller. For service-to-service calls the principal is
 * {@link #service()} which has ADMIN role but is only valid on internal
 * endpoints guarded by the service-token filter.
 */
public record AuthPrincipal(String userId, String username, String displayName, Role role) {

    public static AuthPrincipal service() {
        return new AuthPrincipal("service", "service", "TestHub Service", Role.ADMIN);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
