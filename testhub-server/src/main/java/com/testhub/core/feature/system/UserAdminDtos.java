package com.testhub.core.feature.system;

import com.testhub.common.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin user-management payloads. */
public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    public record CreateUserRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 64, message = "用户名长度需为 3-64")
            String username,
            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 128, message = "密码长度需为 6-128")
            String password,
            @Size(max = 128, message = "显示名最长 128") String displayName,
            Role role,
            Boolean enabled) {
    }

    public record UpdateUserRequest(
            @Size(max = 128, message = "显示名最长 128") String displayName,
            Role role,
            Boolean enabled) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 128, message = "密码长度需为 6-128")
            String password) {
    }
}
