package com.testhub.uiexec.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** GET /internal/v1/cases/{caseId}/execution-context (docs/API-CONTRACT.md §1.8). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionContext(String caseId, String title, String reviewStatus, Bdd bdd, Script script,
                               String failureAnalysis) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bdd(String given, String when, String then) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Script(String id, String scriptType, String name, String entryCommand, String bundleUrl) {
    }
}
