package com.testhub.apiexec.scenario;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ScenarioDtos {

    private ScenarioDtos() {
    }

    public record CreateScenarioRequest(String scenarioId, String runId, String name, String yaml,
                                        Map<String, Object> environment, Map<String, Object> variables,
                                        Integer timeoutSec, String createdBy, String planId,
                                        String planRunId, String caseId) {
    }

    public record CreateScenarioResponse(String taskId, String status) {
    }

    public record ScenarioTaskView(String taskId, String scenarioId, String runId, String name,
                                   String status, String workerId, String summary, String failureReason,
                                   Long durationMs, JsonNode steps, JsonNode report,
                                   String createdBy, Instant createdAt, Instant startedAt,
                                   Instant finishedAt) {
    }

    public record CancelScenarioResponse(String taskId, String status) {
    }

    public record ValidateResponse(boolean valid, String message, int stepCount, List<String> stepTypes) {
    }
}
