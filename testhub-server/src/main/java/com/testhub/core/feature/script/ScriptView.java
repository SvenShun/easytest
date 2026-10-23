package com.testhub.core.feature.script;

import java.time.Instant;

public record ScriptView(String id, String name, ScriptType scriptType, String entryCommand, long sizeBytes,
                         String createdBy, Instant createdAt) {

    public static ScriptView of(ScriptProject script) {
        return new ScriptView(script.getId(), script.getName(), script.getScriptType(), script.getEntryCommand(),
                script.getSizeBytes(), script.getCreatedBy(), script.getCreatedAt());
    }
}
