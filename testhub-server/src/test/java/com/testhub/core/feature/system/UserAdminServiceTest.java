package com.testhub.core.feature.system;

import com.testhub.common.security.PasswordHasher;
import com.testhub.common.security.Role;
import com.testhub.common.web.ApiException;
import com.testhub.core.feature.auth.AppUser;
import com.testhub.core.feature.auth.AppUserRepository;
import com.testhub.core.feature.support.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdminServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserAdminService service = new UserAdminService(users, audit);

    @Test
    void rejectsDuplicateUsername() {
        when(users.existsByUsername("alice")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new UserAdminDtos.CreateUserRequest(
                "alice", "secret123", "Alice", Role.TESTER, true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void createsHashedPassword() {
        when(users.existsByUsername("bob")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new UserAdminDtos.CreateUserRequest("bob", "secret123", "Bob", Role.TESTER, true));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        assertThat(PasswordHasher.verify("secret123", captor.getValue().getPasswordHash())).isTrue();
        verify(audit).record(AuditService.USER_CREATE, captor.getValue().getId(), "username=bob, role=TESTER");
    }

    @Test
    void preventsRemovingLastAdmin() {
        AppUser admin = new AppUser("u-admin", "admin", "hash", "Admin", Role.ADMIN, true, Instant.now());
        when(users.findById("u-admin")).thenReturn(Optional.of(admin));
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.update("u-admin",
                new UserAdminDtos.UpdateUserRequest("Admin", Role.TESTER, true)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo("LAST_ADMIN");
    }

    @Test
    void resetsPassword() {
        AppUser user = new AppUser("u1", "alice", "old", "Alice", Role.TESTER, true, Instant.now());
        when(users.findById("u1")).thenReturn(Optional.of(user));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resetPassword("u1", new UserAdminDtos.ResetPasswordRequest("newpass123"));

        assertThat(PasswordHasher.verify("newpass123", user.getPasswordHash())).isTrue();
        verify(audit).record(AuditService.USER_RESET_PASSWORD, "u1", "username=alice");
    }
}
