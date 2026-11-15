package com.easytest.core.feature.engineeringgit;

import java.time.Instant;
import java.util.List;

public final class EngineeringRepoDtos {

    private EngineeringRepoDtos() {
    }

    public record RepoRequest(String name, String repoType, String repoUrl, String branchName,
                              String rootDirectory, String webhookSecret, Boolean enabled) {
    }

    public record RepoView(String id, String projectId, String name, String repoType, String repoUrl,
                           String branchName, String rootDirectory, boolean enabled, String lastCommit,
                           Instant lastScanAt, String lastError, String createdBy, Instant createdAt,
                           Instant updatedAt, int methodCount) {
    }

    public record RunMethodRequest(String deviceId, Integer timeoutSec, Integer stepTimeoutSec) {
    }

    public record RunMethodResult(String taskId, String status, String deviceId) {
    }

    public record ScanResult(String commit, int scanned, int created, int updated, int removed,
                             List<String> errors, Instant scannedAt) {
    }

    public record MethodView(String id, String repoId, String repoName, String kind, String contractId,
                             String name, String description, String methodPath, List<String> inputs,
                             List<String> outputs, List<String> tags, String payloadJson, String source,
                             Instant updatedAt) {
    }
}
