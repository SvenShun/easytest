package com.easytest.core.feature.apiscenario.git;

import java.time.Instant;
import java.util.List;

public final class ScenarioGitDtos {

    private ScenarioGitDtos() {
    }

    public record ConfigRequest(String repoUrl, String branch, String yamlDirectory,
                                String webhookSecret, Boolean enabled) {
    }

    public record ConfigView(String repoUrl, String branch, String yamlDirectory, boolean enabled,
                             boolean webhookSecretConfigured, String lastCommit,
                             Instant lastSyncAt, String lastError, Instant updatedAt) {
    }

    public record SyncResult(String commit, int scanned, int created, int updated,
                             List<String> errors, Instant syncedAt) {
    }
}
