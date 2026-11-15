package com.easytest.uiexec.client;

import com.easytest.uiexec.client.dto.ExecutionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default source: easytest-server internal execution-context API. */
@Component
@ConditionalOnProperty(prefix = "easytest.uiexec", name = "case-source", havingValue = "easytest", matchIfMissing = true)
public class EasyTestCaseContextSource implements CaseContextSource {

    private final CoreClient coreClient;

    public EasyTestCaseContextSource(CoreClient coreClient) {
        this.coreClient = coreClient;
    }

    @Override
    public ExecutionContext fetch(String caseId) {
        return coreClient.fetchExecutionContext(caseId);
    }

    @Override
    public ExecutionContext fetch(String caseId, String projectId) {
        return coreClient.fetchExecutionContext(caseId, projectId);
    }

    @Override
    public String backend() {
        return "easytest";
    }
}
