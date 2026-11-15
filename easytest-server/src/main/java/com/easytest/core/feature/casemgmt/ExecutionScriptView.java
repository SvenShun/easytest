package com.easytest.core.feature.casemgmt;

import com.easytest.core.feature.script.ScriptType;

/** Script descriptor handed to agents, including a ticket-signed download URL. */
public record ExecutionScriptView(String id, String scriptType, String name, String entryCommand,
                                  String bundleUrl, String contractId, String description,
                                  java.util.List<String> inputs, java.util.List<String> outputs) {

    public static ExecutionScriptView of(com.easytest.core.feature.script.ScriptProject script, String bundleUrl) {
        ScriptType type = script.getScriptType();
        return new ExecutionScriptView(script.getId(), type == null ? null : type.name(), script.getName(),
                script.getEntryCommand(), bundleUrl, script.getContractId(), script.getDescription(),
                script.getInputs(), script.getOutputs());
    }
}
