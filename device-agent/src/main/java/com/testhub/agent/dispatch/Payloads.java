package com.testhub.agent.dispatch;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Callback payloads sent back to ui-execution (contract §2.3). */
public final class Payloads {

    private Payloads() {
    }

    public record LogLine(String level, String message) {
    }

    public record LogBatch(List<LogLine> lines) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Step(
            int seq,
            String status,
            String decision,
            String action,
            long elapsedMs,
            String screenshotBase64,
            String screenshotContentType) {
    }

    public record Artifact(String name, String type, String contentBase64) {
    }

    public record TaskResult(
            String status,
            String failureReason,
            String summary,
            String reasonCategory,
            List<Artifact> artifacts) {

        public static TaskResult success(String summary, List<Artifact> artifacts) {
            return new TaskResult("SUCCESS", null, summary, null, artifacts);
        }
    }
}
