package com.easytest.core.feature.script;

import java.time.Instant;
import java.util.List;

public record ScriptView(String id, String name, ScriptType scriptType, String entryCommand, long sizeBytes,
                         String contractId, String description, List<String> inputs, List<String> outputs,
                         String createdBy, Instant createdAt) {

    public static ScriptView of(ScriptProject script) {
        return new ScriptView(script.getId(), script.getName(), script.getScriptType(), script.getEntryCommand(),
                script.getSizeBytes(), script.getContractId(), script.getDescription(),
                readList(script.getInputContractJson()), readList(script.getOutputContractJson()),
                script.getCreatedBy(), script.getCreatedAt());
    }

    private static List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }
}
