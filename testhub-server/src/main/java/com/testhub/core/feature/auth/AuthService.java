package com.testhub.core.feature.auth;

import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.security.JwtTokenProvider;
import com.testhub.common.security.PasswordHasher;
import com.testhub.common.security.SecurityProperties;
import com.testhub.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Authentication and current-user lookup. */
@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final SecurityProperties securityProperties;

    public AuthService(AppUserRepository userRepository, JwtTokenProvider tokenProvider,
                       SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.securityProperties = securityProperties;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw invalidCredentials();
        }
        AppUser user = userRepository.findByUsername(request.username().trim())
                .filter(AppUser::isEnabled)
                .orElseThrow(AuthService::invalidCredentials);
        if (!PasswordHasher.verify(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        String token = tokenProvider.createToken(
                new AuthPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()));
        return new LoginResponse(token, securityProperties.getTokenTtl().toSeconds(), AccountView.of(user));
    }

    public AccountView currentUser() {
        AuthPrincipal principal = CurrentUser.require();
        return userRepository.findById(principal.userId())
                .map(AccountView::of)
                .orElseGet(() -> new AccountView(principal.userId(), principal.username(),
                        principal.displayName(), principal.role()));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
