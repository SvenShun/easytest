package com.testhub.core.feature.apiscenario;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiScenarioDtos {

    private ApiScenarioDtos() {
    }

    public record ScenarioRequest(String name, String description, String yaml, String environmentId,
                                  Boolean enabled) {
    }

    public record ScenarioView(String id, String name, String description, String yaml,
                               String environmentId, boolean enabled, String createdBy,
                               Instant createdAt, Instant updatedAt) {
    }

    public record RunRequest(String environmentId, Map<String, Object> variables, Integer timeoutSec) {
    }

    public record RunView(String id, String scenarioId, String scenarioName, String taskId, String status,
                          String environmentId, String summary, String failureReason, Long durationMs,
                          JsonNode steps, JsonNode report, Map<String, Object> variables,
                          String createdBy, Instant createdAt, Instant finishedAt) {
    }

    public record RunListView(List<RunView> items, long total, int page, int size) {
    }

    public record ValidateRequest(String yaml) {
    }
}
