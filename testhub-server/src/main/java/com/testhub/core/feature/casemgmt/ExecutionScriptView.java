package com.testhub.core.feature.casemgmt;

import com.testhub.core.feature.script.ScriptType;

/** Script descriptor handed to agents, including a ticket-signed download URL. */
public record ExecutionScriptView(String id, String scriptType, String name, String entryCommand,
                                  String bundleUrl) {

    public static ExecutionScriptView of(com.testhub.core.feature.script.ScriptProject script, String bundleUrl) {
        ScriptType type = script.getScriptType();
        return new ExecutionScriptView(script.getId(), type == null ? null : type.name(), script.getName(),
                script.getEntryCommand(), bundleUrl);
    }
}
