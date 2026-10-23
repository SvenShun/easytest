package com.testhub.uiexec.client;

import com.testhub.common.security.AuthFilter;
import com.testhub.common.security.SecurityProperties;
import com.testhub.uiexec.client.dto.ExecutionContext;
import com.testhub.uiexec.client.dto.FailureAnalysisPayload;
import com.testhub.uiexec.client.dto.ModelCredential;
import com.testhub.uiexec.client.dto.PlanItemResultPayload;
import com.testhub.uiexec.config.UiExecProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * Client for testhub-server internal APIs. Read paths propagate a
 * {@link CoreClientException}; write-backs (failure analysis, plan item result)
 * are best-effort and only logged, so a core outage never blocks task
 * termination.
 */
@Component
public class CoreClient {

    private static final Logger log = LoggerFactory.getLogger(CoreClient.class);

    private final RestClient client;

    public CoreClient(RestClient.Builder builder, UiExecProperties properties, SecurityProperties security) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.client = builder
                .baseUrl(properties.getCoreBaseUrl())
                .requestFactory(factory)
                .defaultHeader(AuthFilter.SERVICE_TOKEN_HEADER, security.getServiceToken())
                .build();
    }

    public ExecutionContext fetchExecutionContext(String caseId) {
        try {
            ExecutionContext context = client.get()
                    .uri("/internal/v1/cases/{caseId}/execution-context", caseId)
                    .retrieve()
                    .body(ExecutionContext.class);
            if (context == null) {
                throw new CoreClientException("core 返回空的执行上下文", 502);
            }
            return context;
        } catch (RestClientResponseException e) {
            throw new CoreClientException("获取用例执行上下文失败: HTTP " + e.getStatusCode().value(),
                    e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new CoreClientException("core 不可达: " + e.getMessage(), 0, e);
        }
    }

    /** Empty when the vision model is not configured (core answers 409). */
    public Optional<ModelCredential> fetchVisionCredential() {
        try {
            ModelCredential credential = client.get()
                    .uri("/internal/v1/model-configs/vision/credential")
                    .retrieve()
                    .body(ModelCredential.class);
            return Optional.ofNullable(credential);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                return Optional.empty();
            }
            throw new CoreClientException("获取视觉模型凭证失败: HTTP " + e.getStatusCode().value(),
                    e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new CoreClientException("core 不可达: " + e.getMessage(), 0, e);
        }
    }

    /** Best-effort write-back of the failure analysis; failures are logged only. */
    public void updateFailureAnalysis(String caseId, FailureAnalysisPayload payload) {
        try {
            client.put()
                    .uri("/internal/v1/cases/{caseId}/failure-analysis", caseId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("回写用例失败分析失败 caseId={} taskId={}: {}", caseId, payload.taskId(), e.getMessage());
        }
    }

    /** Best-effort plan-run item callback; failures are logged only. */
    public void postPlanItemResult(PlanItemResultPayload payload) {
        if (payload.planRunId() == null || payload.planRunId().isBlank()) {
            return;
        }
        try {
            client.post()
                    .uri("/internal/v1/plan-runs/items/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("回写计划执行结果失败 planRunId={} taskId={}: {}",
                    payload.planRunId(), payload.taskId(), e.getMessage());
        }
    }
}
