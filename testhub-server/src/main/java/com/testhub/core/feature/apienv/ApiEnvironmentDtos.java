package com.testhub.core.feature.apienv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiEnvironmentDtos {

    private ApiEnvironmentDtos() {
    }

    public record AuthConfig(String type, String token, String username, String password,
                             String headerName, String headerValue, String prefix,
                             String loginMethod, String loginPath, Map<String, String> loginHeaders,
                             String loginBody, String tokenPath, String tokenHeader, String tokenPrefix) {
    }

    public record EnvironmentRequest(
            @NotBlank(message = "环境名称不能为空") @Size(max = 128) String name,
            @NotBlank(message = "Base URL 不能为空") @Size(max = 1000) String baseUrl,
            Map<String, String> headers,
            Map<String, String> variables,
            AuthConfig auth,
            Boolean enabled) {
    }

    public record EnvironmentView(String id, String name, String baseUrl, Map<String, String> headers,
                                  Map<String, String> variables, AuthConfig auth, boolean enabled,
                                  String createdBy, Instant createdAt, Instant updatedAt) {
    }

    public record EnvironmentListView(List<EnvironmentView> items, long total, int page, int size) {
    }
}
