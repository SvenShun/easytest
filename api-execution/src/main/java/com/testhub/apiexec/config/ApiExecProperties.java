package com.testhub.apiexec.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime configuration for api-execution ({@code testhub.apiexec.*}). */
@ConfigurationProperties(prefix = "testhub.apiexec")
public class ApiExecProperties {

    /** Base URL of testhub-server, used for internal (service-token) callbacks. */
    private String coreBaseUrl = "http://127.0.0.1:8080";

    /** Public base URL of this service; failure report URLs are derived from it. */
    private String publicBaseUrl = "http://127.0.0.1:8084";

    /**
     * Optional override for the service token used on core callbacks. When
     * blank the shared {@code testhub.security.service-token} is used, so the
     * inbound and outbound token are the same by default.
     */
    private String serviceToken = "";

    /** Number of HTTP execution workers (bounded pool, FIFO queue). */
    private int workers = 4;

    /** Fallback request timeout when the task does not carry one. */
    private int defaultTimeoutSec = 30;

    /** Disables the plan-run result callback (useful for isolated tests). */
    private boolean callbackEnabled = true;

    public String getCoreBaseUrl() {
        return coreBaseUrl;
    }

    public void setCoreBaseUrl(String coreBaseUrl) {
        this.coreBaseUrl = coreBaseUrl;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public int getWorkers() {
        return workers;
    }

    public void setWorkers(int workers) {
        this.workers = workers;
    }

    public int getDefaultTimeoutSec() {
        return defaultTimeoutSec;
    }

    public void setDefaultTimeoutSec(int defaultTimeoutSec) {
        this.defaultTimeoutSec = defaultTimeoutSec;
    }

    public boolean isCallbackEnabled() {
        return callbackEnabled;
    }

    public void setCallbackEnabled(boolean callbackEnabled) {
        this.callbackEnabled = callbackEnabled;
    }

    /** Absolute report URL for a task, rooted at {@link #publicBaseUrl}. */
    public String failureReportUrl(String taskId) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/v1/api-execution/tasks/" + taskId + "/report";
    }
}
