package com.easytest.uiexec.client;

import com.easytest.uiexec.client.dto.ExecutionContext;

/**
 * Source of truth for the case being executed. The default is EasyTest's own
 * case-management module; {@code easytest.uiexec.case-source=metersphere} swaps
 * in MeterSphere v3 (functional case detail API).
 */
public interface CaseContextSource {

    ExecutionContext fetch(String caseId);

    /** Fetch under a project context when the backend supports it. */
    default ExecutionContext fetch(String caseId, String projectId) {
        return fetch(caseId);
    }

    /** Short identifier used in logs and audit. */
    String backend();
}
