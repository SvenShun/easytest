package com.easytest.uiexec.client;

import com.easytest.uiexec.client.dto.FailureAnalysisPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default sink: PUT easytest-server /internal/v1/cases/{id}/failure-analysis. */
@Component
@ConditionalOnProperty(prefix = "easytest.uiexec", name = "case-source", havingValue = "easytest", matchIfMissing = true)
public class EasyTestFailureAnalysisSink implements FailureAnalysisSink {

    private final CoreClient coreClient;

    public EasyTestFailureAnalysisSink(CoreClient coreClient) {
        this.coreClient = coreClient;
    }

    @Override
    public void write(String caseId, FailureAnalysisPayload payload) {
        coreClient.updateFailureAnalysis(caseId, payload);
    }

    @Override
    public String backend() {
        return "easytest";
    }
}
