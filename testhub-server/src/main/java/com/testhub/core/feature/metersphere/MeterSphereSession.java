package com.testhub.core.feature.metersphere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MeterSphere session handling for the result write-back.
 *
 * <p>MeterSphere v3 authenticates with a Shiro session id ({@code X-AUTH-TOKEN})
 * plus a CSRF token. A deployment can either inject both values (short-lived) or
 * configure {@code username/password}; in the latter case the session is created
 * on first use and re-created once when a call fails (token expiry).</p>
 */
@Component
public class MeterSphereSession {

    private static final Logger log = LoggerFactory.getLogger(MeterSphereSession.class);

    private final MeterSphereProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient loginClient;
    private volatile Credentials credentials;

    public MeterSphereSession(MeterSphereProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.loginClient = RestClient.builder()
                .baseUrl(trim(properties.getBaseUrl()))
                .build();
    }

    public synchronized Map<String, String> headers() {
        Credentials current = credentials;
        if (current == null || current.expired()) {
            current = resolve();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-AUTH-TOKEN", current.token());
        headers.put("CSRF-TOKEN", current.csrf());
        if (hasText(properties.getProjectId())) {
            headers.put("PROJECT", properties.getProjectId());
        }
        return headers;
    }

    /** Forces a new login on the next call. */
    public synchronized void refresh() {
        credentials = null;
    }

    public boolean available() {
        return hasText(properties.getToken()) || hasText(properties.getUsername());
    }

    private Credentials resolve() {
        if (hasText(properties.getToken()) && hasText(properties.getCsrfToken())) {
            credentials = new Credentials(properties.getToken(), properties.getCsrfToken(), null);
            return credentials;
        }
        if (!hasText(properties.getUsername()) || !hasText(properties.getPassword())) {
            throw new IllegalStateException(
                    "MeterSphere 会话未配置：请设置 TESTHUB_METERSHPHERE_TOKEN/CSRF_TOKEN 或 USERNAME/PASSWORD");
        }
        try {
            String body = loginClient.post()
                    .uri("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", properties.getUsername(), "password", properties.getPassword()))
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body == null ? "{}" : body);
            if (json.path("code").asInt() != 100200) {
                throw new IllegalStateException("MeterSphere 登录失败: " + json.path("message").asText());
            }
            JsonNode data = json.path("data");
            String token = data.path("sessionId").asText();
            String csrf = data.path("csrfToken").asText();
            if (!hasText(token) || !hasText(csrf)) {
                throw new IllegalStateException("MeterSphere 登录响应缺少 sessionId/csrfToken");
            }
            log.info("MeterSphere session created user={}", properties.getUsername());
            credentials = new Credentials(token, csrf, null);
            return credentials;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("MeterSphere 登录失败: " + e.getMessage(), e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trim(String value) {
        String base = value == null || value.isBlank() ? "http://127.0.0.1:8082" : value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private record Credentials(String token, String csrf, java.time.Instant expiresAt) {
        boolean expired() {
            return expiresAt != null && java.time.Instant.now().isAfter(expiresAt);
        }
    }
}
