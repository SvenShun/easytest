package com.testhub.core.feature.auth;

/** Login payload. */
public record LoginRequest(String username, String password) {
}
