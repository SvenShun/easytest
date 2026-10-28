package com.testhub.apiexec.scenario;

import java.util.List;
import java.util.Map;

/** Result of executing a whole YAML scenario. */
public record ScenarioRunResult(String status, String failureReason, String summary, String error,
                                List<ScenarioStepResult> steps, Map<String, Object> variables,
                                long durationMs) {
}
