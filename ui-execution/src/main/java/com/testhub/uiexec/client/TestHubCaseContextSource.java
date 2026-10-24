package com.testhub.uiexec.client;

import com.testhub.uiexec.client.dto.ExecutionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default source: testhub-server internal execution-context API. */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "case-source", havingValue = "testhub", matchIfMissing = true)
public class TestHubCaseContextSource implements CaseContextSource {

    private final CoreClient coreClient;

    public TestHubCaseContextSource(CoreClient coreClient) {
        this.coreClient = coreClient;
    }

    @Override
    public ExecutionContext fetch(String caseId) {
        return coreClient.fetchExecutionContext(caseId);
    }

    @Override
    public String backend() {
        return "testhub";
    }
}
