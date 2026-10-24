package com.testhub.uiexec.client;

import com.testhub.uiexec.client.dto.FailureAnalysisPayload;

/**
 * Destination for the automatic failure analysis after a task fails. Default is
 * TestHub case-management; MeterSphere mode writes a case comment instead.
 */
public interface FailureAnalysisSink {

    void write(String caseId, FailureAnalysisPayload payload);

    String backend();
}
