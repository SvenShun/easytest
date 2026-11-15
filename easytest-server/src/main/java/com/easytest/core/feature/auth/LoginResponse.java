package com.easytest.core.feature.auth;

/** Login response exactly as defined by the contract. */
public record LoginResponse(String token, long expiresInSec, AccountView user) {
}
