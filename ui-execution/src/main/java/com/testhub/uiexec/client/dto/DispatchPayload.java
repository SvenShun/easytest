package com.testhub.uiexec.client.dto;

import com.testhub.uiexec.domain.ExecutorType;

import java.util.List;

/** POST {agent.baseUrl}/agent/tasks payload (docs/API-CONTRACT.md §2.6). */
public record DispatchPayload(String taskId, ExecutorType executorType, String caseId, String caseTitle,
                              Bdd bdd, Script script, Device device, Skills skills, Timeouts timeouts,
                              Model model, Callbacks callbacks) {

    public record Bdd(String given, String when, String then) {
    }

    public record Script(String id, String scriptType, String name, String entryCommand, String bundleUrl) {
    }

    public record Device(String serial, String platform, List<String> tags) {
    }

    public record Skills(String repo, String commit) {
    }

    public record Timeouts(int totalSec, int stepSec) {
    }

    public record Model(String provider, String endpoint, String apiKey, String modelId) {
    }

    public record Callbacks(String stepUrl, String logUrl, String resultUrl, String token) {
    }
}
