package com.easytest.core.feature.casemgmt;

import com.easytest.core.feature.script.ScriptProject;
import com.easytest.core.feature.script.ScriptType;

/** Script summary embedded in a case payload. */
public record CaseScriptView(String id, String name, ScriptType scriptType, String entryCommand,
                             String contractId, String description, java.util.List<String> inputs,
                             java.util.List<String> outputs) {

    public static CaseScriptView of(ScriptProject script) {
        return new CaseScriptView(script.getId(), script.getName(), script.getScriptType(),
                script.getEntryCommand(), script.getContractId(), script.getDescription(),
                script.getInputs(), script.getOutputs());
    }
}
