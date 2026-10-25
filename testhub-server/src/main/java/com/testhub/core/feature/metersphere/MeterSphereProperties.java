package com.testhub.core.feature.metersphere;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MeterSphere connectivity for plan-result write-back
 * ({@code testhub.metersphere.*}).
 *
 * <p>MeterSphere v3 requires a Shiro session id plus a CSRF token on every
 * authenticated call; both are obtained from {@code POST /login}. They are
 * configuration values in V1 (short-lived credentials), see docs/METERSPHERE-INTEGRATION.md.
 */
@Component
@ConfigurationProperties(prefix = "testhub.metersphere")
public class MeterSphereProperties {

    /** Enables result write-back to MeterSphere. */
    private boolean enabled = false;
    private String baseUrl = "http://127.0.0.1:8082";
    private String projectId = "";
    private String token = "";
    private String csrfToken = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    public void setCsrfToken(String csrfToken) {
        this.csrfToken = csrfToken;
    }
}
