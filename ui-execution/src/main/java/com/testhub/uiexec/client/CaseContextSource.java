package com.testhub.uiexec.client;

import com.testhub.uiexec.client.dto.ExecutionContext;

/**
 * Source of truth for the case being executed. The default is TestHub's own
 * case-management module; {@code testhub.uiexec.case-source=metersphere} swaps
 * in MeterSphere v3 (functional case detail API).
 */
public interface CaseContextSource {

    ExecutionContext fetch(String caseId);

    /** Short identifier used in logs and audit. */
    String backend();
}
