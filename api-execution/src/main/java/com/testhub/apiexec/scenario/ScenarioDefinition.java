package com.testhub.apiexec.scenario;

import java.util.List;
import java.util.Map;

/** Parsed YAML scenario. Steps remain maps so the executor can evolve without migrations. */
public record ScenarioDefinition(String name, Integer timeoutSec, Boolean continueOnFailure,
                                 Map<String, Object> variables, Map<String, Object> environment,
                                 Map<String, Object> datasets, List<Map<String, Object>> steps) {
}
