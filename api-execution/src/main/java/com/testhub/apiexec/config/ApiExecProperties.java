package com.testhub.apiexec.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;
import java.util.UUID;

/** Runtime configuration for api-execution ({@code testhub.apiexec.*}). */
@ConfigurationProperties(prefix = "testhub.apiexec")
public class ApiExecProperties {

    private static final String DEFAULT_INSTANCE_ID = createDefaultInstanceId();

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

    /** Number of worker threads polling the queue on this instance (0 disables workers). */
    private int workers = 4;

    /** Queue backend: {@code memory} (default) or {@code redis}. */
    private String queue = "memory";

    /** Prefix for Redis queue / dedup / cancellation keys. */
    private String redisKeyPrefix = "testhub:apiexec:";

    /** Stable worker identity prefix; defaults to hostname + random suffix. */
    private String instanceId = "";

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

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    public String getInstanceId() {
        return instanceId == null || instanceId.isBlank() ? DEFAULT_INSTANCE_ID : instanceId.trim();
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
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

    private static String createDefaultInstanceId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "apiexec";
        }
        String sanitized = host == null ? "" : host.replaceAll("[^A-Za-z0-9._-]", "-");
        if (sanitized.isBlank()) {
            sanitized = "apiexec";
        }
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return sanitized + "-" + suffix;
    }
}
