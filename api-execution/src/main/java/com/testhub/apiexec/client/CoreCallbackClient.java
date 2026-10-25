package com.testhub.apiexec.client;

import com.testhub.apiexec.config.ApiExecProperties;
import com.testhub.common.security.AuthFilter;
import com.testhub.common.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Best-effort plan-run item callback to testhub-server. Failures — including an
 * unreachable core — are logged at WARN and never change the task state.
 */
@Component
public class CoreCallbackClient {

    private static final Logger log = LoggerFactory.getLogger(CoreCallbackClient.class);

    private final RestClient client;
    private final boolean enabled;

    public CoreCallbackClient(RestClient.Builder builder, ApiExecProperties properties,
                              SecurityProperties securityProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        this.enabled = properties.isCallbackEnabled();
        String token = properties.getServiceToken() == null || properties.getServiceToken().isBlank()
                ? securityProperties.getServiceToken()
                : properties.getServiceToken();
        this.client = builder
                .baseUrl(trimTrailingSlash(properties.getCoreBaseUrl()))
                .requestFactory(factory)
                .defaultHeader(AuthFilter.SERVICE_TOKEN_HEADER, token)
                .build();
    }

    public void postPlanItemResult(PlanItemResultPayload payload) {
        if (!enabled) {
            return;
        }
        if (payload == null || payload.planRunId() == null || payload.planRunId().isBlank()) {
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
        } catch (RuntimeException e) {
            log.warn("回写计划执行结果异常 planRunId={} taskId={}: {}",
                    payload.planRunId(), payload.taskId(), e.getMessage());
        }
    }

    private static String trimTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
