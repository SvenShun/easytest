package com.testhub.core.feature.auth;

import com.testhub.common.security.PublicApi;
import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PublicApi
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
    @GetMapping("/me")
    public AccountView me() {
        return authService.currentUser();
    }
}
