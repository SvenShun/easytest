package com.testhub.agent.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration bound from {@code testhub.agent.*}.
 */
@ConfigurationProperties(prefix = "testhub.agent")
public class AgentProperties {

    /** Display name used for registration. */
    private String name = "device-agent-1";
    /** Host advertised to ui-execution. */
    private String host = "127.0.0.1";
    /** Public base URL of this agent (passed to ai-runtime as the device base URL). */
    private String baseUrl = "http://127.0.0.1:8090";
    /** ui-execution base URL. */
    private String uiExecutionUrl = "http://127.0.0.1:8081";
    /** Shared agent token for {@code X-Agent-Token}. */
    private String token = "testhub-dev-agent-token";
    /** ai-runtime base URL. */
    private String runtimeUrl = "http://127.0.0.1:8095";
    /** simulated | adb | auto. */
    private String mode = "simulated";
    /** Directory for task workspaces and the Skills cache. */
    private String dataDir = "./data/agent";
    /** Heartbeat interval in seconds. */
    private int heartbeatIntervalSec = 20;
    /** Internal switch used by tests to disable registration/heartbeat. */
    private boolean registrationEnabled = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUiExecutionUrl() {
        return uiExecutionUrl;
    }

    public void setUiExecutionUrl(String uiExecutionUrl) {
        this.uiExecutionUrl = uiExecutionUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRuntimeUrl() {
        return runtimeUrl;
    }

    public void setRuntimeUrl(String runtimeUrl) {
        this.runtimeUrl = runtimeUrl;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public int getHeartbeatIntervalSec() {
        return heartbeatIntervalSec;
    }

    public void setHeartbeatIntervalSec(int heartbeatIntervalSec) {
        this.heartbeatIntervalSec = heartbeatIntervalSec;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    /** Normalized absolute data directory. */
    public Path dataPath() {
        return Paths.get(dataDir).toAbsolutePath().normalize();
    }

    /** Base URL without a trailing slash. */
    public String baseUrlNoTrailingSlash() {
        return stripTrailingSlash(baseUrl);
    }

    public String runtimeUrlNoTrailingSlash() {
        return stripTrailingSlash(runtimeUrl);
    }

    public String uiExecutionUrlNoTrailingSlash() {
        return stripTrailingSlash(uiExecutionUrl);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
