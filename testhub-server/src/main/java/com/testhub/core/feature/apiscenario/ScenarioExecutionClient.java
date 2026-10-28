package com.testhub.core.feature.apiscenario;

import com.fasterxml.jackson.databind.JsonNode;

/** Port to api-execution scenario endpoints. */
public interface ScenarioExecutionClient {
    JsonNode create(java.util.Map<String, Object> payload);
    JsonNode detail(String taskId);
    JsonNode validate(String yaml);
}
