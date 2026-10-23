package com.testhub.core.feature.casemgmt;

import com.testhub.core.feature.script.ScriptProject;
import com.testhub.core.feature.script.ScriptType;

/** Script summary embedded in a case payload. */
public record CaseScriptView(String id, String name, ScriptType scriptType, String entryCommand) {

    public static CaseScriptView of(ScriptProject script) {
        return new CaseScriptView(script.getId(), script.getName(), script.getScriptType(),
                script.getEntryCommand());
    }
}
