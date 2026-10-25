package com.testhub.core.feature.system;

import com.testhub.common.security.PasswordHasher;
import com.testhub.common.security.Role;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.core.feature.auth.AppUser;
import com.testhub.core.feature.auth.AppUserRepository;
import com.testhub.core.feature.auth.UserView;
import com.testhub.core.feature.support.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Admin-only user lifecycle: create, update, enable/disable and reset password. */
@Service
public class UserAdminService {

    private final AppUserRepository users;
    private final AuditService auditService;

    public UserAdminService(AppUserRepository users, AuditService auditService) {
        this.users = users;
        this.auditService = auditService;
    }

    @Transactional
    public UserView create(UserAdminDtos.CreateUserRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        if (username.isEmpty()) {
            throw ApiException.badRequest("用户名不能为空");
        }
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("用户名已存在: " + username);
        }
        Role role = request.role() == null ? Role.TESTER : request.role();
        AppUser user = new AppUser(Ids.newId("u"), username, PasswordHasher.hash(request.password()),
                blankToNull(request.displayName()), role,
                request.enabled() == null || request.enabled(), Instant.now());
        users.save(user);
        auditService.record(AuditService.USER_CREATE, user.getId(),
                "username=" + username + ", role=" + role);
        return UserView.of(user);
    }

    @Transactional
    public UserView update(String id, UserAdminDtos.UpdateUserRequest request) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> ApiException.notFound("用户不存在: " + id));
        Role newRole = request.role() == null ? user.getRole() : request.role();
        boolean newEnabled = request.enabled() == null ? user.isEnabled() : request.enabled();
        boolean removesAdmin = user.getRole() == Role.ADMIN
                && (newRole != Role.ADMIN || !newEnabled);
        if (removesAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN",
                    "系统必须保留至少一个启用状态的管理员");
        }
        user.setDisplayName(blankToNull(request.displayName()));
        user.setRole(newRole);
        user.setEnabled(newEnabled);
        users.save(user);
        auditService.record(AuditService.USER_UPDATE, user.getId(),
                "role=" + newRole + ", enabled=" + newEnabled);
        return UserView.of(user);
    }

    @Transactional
    public UserView resetPassword(String id, UserAdminDtos.ResetPasswordRequest request) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> ApiException.notFound("用户不存在: " + id));
        user.setPasswordHash(PasswordHasher.hash(request.password()));
        users.save(user);
        auditService.record(AuditService.USER_RESET_PASSWORD, user.getId(), "username=" + user.getUsername());
        return UserView.of(user);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
