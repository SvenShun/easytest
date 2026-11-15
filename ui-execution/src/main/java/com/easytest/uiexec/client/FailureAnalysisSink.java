package com.easytest.uiexec.client;

import com.easytest.uiexec.client.dto.FailureAnalysisPayload;

/**
 * Destination for the automatic failure analysis after a task fails. Default is
 * EasyTest case-management; MeterSphere mode writes a case comment instead.
 */
public interface FailureAnalysisSink {

    void write(String caseId, FailureAnalysisPayload payload);

    String backend();
}
