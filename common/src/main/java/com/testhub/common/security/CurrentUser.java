package com.testhub.common.security;

import com.testhub.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** Request-scoped holder for the authenticated caller. */
public final class CurrentUser {

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    public static AuthPrincipal get() {
        return HOLDER.get();
    }

    public static AuthPrincipal require() {
        AuthPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "登录状态已失效，请重新登录");
        }
        return principal;
    }

    public static boolean isAdmin() {
        AuthPrincipal principal = HOLDER.get();
        return principal != null && principal.role() == Role.ADMIN;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
