package com.testhub.uiexec.client;

import com.testhub.uiexec.client.dto.FailureAnalysisPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default sink: PUT testhub-server /internal/v1/cases/{id}/failure-analysis. */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "case-source", havingValue = "testhub", matchIfMissing = true)
public class TestHubFailureAnalysisSink implements FailureAnalysisSink {

    private final CoreClient coreClient;

    public TestHubFailureAnalysisSink(CoreClient coreClient) {
        this.coreClient = coreClient;
    }

    @Override
    public void write(String caseId, FailureAnalysisPayload payload) {
        coreClient.updateFailureAnalysis(caseId, payload);
    }

    @Override
    public String backend() {
        return "testhub";
    }
}
