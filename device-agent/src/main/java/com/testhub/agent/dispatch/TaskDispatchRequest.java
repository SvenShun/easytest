package com.testhub.agent.dispatch;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Dispatch payload sent by ui-execution (contract §2.6).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskDispatchRequest(
        String taskId,
        String executorType,
        String caseId,
        String caseTitle,
        Bdd bdd,
        Script script,
        Device device,
        Skills skills,
        Timeouts timeouts,
        Model model,
        Callbacks callbacks) {

    public static final String EXECUTOR_SCRIPT = "SCRIPT";
    public static final String EXECUTOR_AI_RUNTIME = "AI_RUNTIME";

    public static final int DEFAULT_TOTAL_SEC = 120;
    public static final int DEFAULT_STEP_SEC = 30;
    public static final int MAX_STEPS = 25;

    public int totalSec() {
        if (timeouts == null || timeouts.totalSec() == null || timeouts.totalSec() <= 0) {
            return DEFAULT_TOTAL_SEC;
        }
        return timeouts.totalSec();
    }

    public int stepSec() {
        if (timeouts == null || timeouts.stepSec() == null || timeouts.stepSec() <= 0) {
            return DEFAULT_STEP_SEC;
        }
        return timeouts.stepSec();
    }

    public String deviceSerial() {
        return device == null ? null : device.serial();
    }

    public String callbackToken(String fallback) {
        if (callbacks != null && callbacks.token() != null && !callbacks.token().isBlank()) {
            return callbacks.token();
        }
        return fallback;
    }

    public boolean isScript() {
        return EXECUTOR_SCRIPT.equals(executorType);
    }

    public boolean isAiRuntime() {
        return EXECUTOR_AI_RUNTIME.equals(executorType);
    }

    public String safeCaseTitle() {
        return caseTitle == null || caseTitle.isBlank() ? "task " + taskId : caseTitle;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bdd(String given, String when, String then) {

        public String safeGiven() {
            return given == null ? "" : given;
        }

        public String safeWhen() {
            return when == null ? "" : when;
        }

        public String safeThen() {
            return then == null ? "" : then;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Script(
            String id,
            String scriptType,
            String name,
            String entryCommand,
            String bundleUrl) {

        public String safeEntryCommand() {
            return entryCommand == null || entryCommand.isBlank() ? "sh run.sh" : entryCommand;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Device(String serial, String platform, List<String> tags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Skills(String repo, String commit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Timeouts(Integer totalSec, Integer stepSec) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Model(String provider, String endpoint, String apiKey, String modelId) {

        public String safeProvider() {
            return provider == null || provider.isBlank() ? "mock" : provider;
        }

        public String safeEndpoint() {
            return endpoint == null ? "" : endpoint;
        }

        public String safeApiKey() {
            return apiKey == null ? "" : apiKey;
        }

        public String safeModelId() {
            return modelId == null || modelId.isBlank() ? "mock-vision" : modelId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Callbacks(String stepUrl, String logUrl, String resultUrl, String token) {
    }
}
