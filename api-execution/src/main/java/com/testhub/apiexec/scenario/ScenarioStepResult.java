package com.testhub.apiexec.scenario;

import com.testhub.apiexec.assertion.AssertionResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One executed scenario step, serialized into the run report. */
public record ScenarioStepResult(int seq, String name, String type, String status, long durationMs,
                                 Map<String, Object> request, Map<String, Object> response,
                                 List<AssertionResult> assertions, Map<String, Object> extracted,
                                 String error) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("seq", seq);
        map.put("name", name);
        map.put("type", type);
        map.put("status", status);
        map.put("durationMs", durationMs);
        if (request != null && !request.isEmpty()) {
            map.put("request", request);
        }
        if (response != null && !response.isEmpty()) {
            map.put("response", response);
        }
        if (assertions != null && !assertions.isEmpty()) {
            map.put("assertions", assertions);
        }
        if (extracted != null && !extracted.isEmpty()) {
            map.put("extracted", extracted);
        }
        if (error != null) {
            map.put("error", error);
        }
        return map;
    }
}
