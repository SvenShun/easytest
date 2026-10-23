package com.testhub.core.feature.auth;

import com.testhub.common.security.Role;

/** Session user shape used by login / me (contract §1.1). */
public record AccountView(String id, String username, String displayName, Role role) {

    public static AccountView of(AppUser user) {
        return new AccountView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
