package com.testhub.uiexec.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime configuration for ui-execution ({@code testhub.uiexec.*}). */
@ConfigurationProperties(prefix = "testhub.uiexec")
public class UiExecProperties {

    /** Base URL of testhub-server, used for internal (service-token) calls. */
    private String coreBaseUrl = "http://127.0.0.1:8080";

    /** Public base URL of this service; artifact URLs are derived from it. */
    private String publicBaseUrl = "http://127.0.0.1:8081";

    /**
     * Base URL the device-agent uses to reach this service for callbacks.
     * Defaults to {@link #publicBaseUrl}; container / multi-host deployments set
     * it to an internal address (e.g. http://ui-execution:8081).
     */
    private String callbackBaseUrl;

    /** Root directory of the local artifact store. */
    private String artifactDir = "./data/artifacts";

    /** {@code local} (default) or {@code minio}. */
    private String artifactStore = "local";

    /** {@code memory} (default) or {@code redis}. */
    private String queueStore = "memory";

    /** Seconds without heartbeat after which an agent / device is considered offline. */
    private int agentOfflineSeconds = 90;

    /** Enables the scheduled watchdog (timeouts + lost agent heartbeat). */
    private boolean watchdogEnabled = true;

    private final Minio minio = new Minio();

    /** {@code testhub} (default) or {@code metersphere}. */
    private String caseSource = "testhub";

    private final MeterSphere metersphere = new MeterSphere();

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

    public String getCallbackBaseUrl() {
        return callbackBaseUrl == null || callbackBaseUrl.isBlank() ? publicBaseUrl : callbackBaseUrl;
    }

    public void setCallbackBaseUrl(String callbackBaseUrl) {
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public String getArtifactDir() {
        return artifactDir;
    }

    public void setArtifactDir(String artifactDir) {
        this.artifactDir = artifactDir;
    }

    public String getArtifactStore() {
        return artifactStore;
    }

    public void setArtifactStore(String artifactStore) {
        this.artifactStore = artifactStore;
    }

    public String getQueueStore() {
        return queueStore;
    }

    public void setQueueStore(String queueStore) {
        this.queueStore = queueStore;
    }

    public int getAgentOfflineSeconds() {
        return agentOfflineSeconds;
    }

    public void setAgentOfflineSeconds(int agentOfflineSeconds) {
        this.agentOfflineSeconds = agentOfflineSeconds;
    }

    public boolean isWatchdogEnabled() {
        return watchdogEnabled;
    }

    public void setWatchdogEnabled(boolean watchdogEnabled) {
        this.watchdogEnabled = watchdogEnabled;
    }

    public Minio getMinio() {
        return minio;
    }

    /** Absolute URL for an artifact stored under {@code relativePath}. */
    public String artifactUrl(String relativePath) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = relativePath.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return base + "/artifacts/" + path;
    }

    public String getCaseSource() {
        return caseSource;
    }

    public void setCaseSource(String caseSource) {
        this.caseSource = caseSource;
    }

    public MeterSphere getMetersphere() {
        return metersphere;
    }

    /** MeterSphere v3 connectivity and case-mapping options. */
    public static class MeterSphere {
        private String baseUrl = "http://127.0.0.1:8081";
        private String token = "";
        private String csrfToken = "";
        private String projectId = "";
        private boolean requireReview = false;
        private String approvedReviewStatuses = "COMPLETED";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public boolean isRequireReview() {
            return requireReview;
        }

        public void setRequireReview(boolean requireReview) {
            this.requireReview = requireReview;
        }

        public String getApprovedReviewStatuses() {
            return approvedReviewStatuses;
        }

        public void setApprovedReviewStatuses(String approvedReviewStatuses) {
            this.approvedReviewStatuses = approvedReviewStatuses;
        }
    }

    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "testhub";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }
}
