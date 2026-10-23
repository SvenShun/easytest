package com.testhub.common.security;

/**
 * Platform roles. V1 keeps a flat role model (one role per user), matching the
 * permission matrix in the architecture document.
 */
public enum Role {
    ADMIN,
    TESTER,
    BUSINESS,
    OPS
}
