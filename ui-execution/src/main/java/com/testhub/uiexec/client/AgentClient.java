package com.testhub.uiexec.client;

import com.testhub.common.security.AuthFilter;
import com.testhub.common.security.SecurityProperties;
import com.testhub.uiexec.client.dto.DispatchPayload;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client for the device agent ({@code POST /agent/tasks}, {@code DELETE
 * /agent/tasks/{taskId}}), authenticated with {@code X-Agent-Token}.
 */
@Component
public class AgentClient {

    private final RestClient.Builder builder;
    private final SecurityProperties security;

    public AgentClient(RestClient.Builder builder, SecurityProperties security) {
        this.builder = builder;
        this.security = security;
    }

    public void dispatch(String agentBaseUrl, DispatchPayload payload) {
        if (agentBaseUrl == null || agentBaseUrl.isBlank()) {
            throw new AgentUnavailableException("Agent 未提供 baseUrl");
        }
        try {
            client(agentBaseUrl).post()
                    .uri("/agent/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new AgentUnavailableException("下发任务失败: " + e.getMessage(), e);
        }
    }

    public void cancel(String agentBaseUrl, String taskId) {
        if (agentBaseUrl == null || agentBaseUrl.isBlank()) {
            throw new AgentUnavailableException("Agent 未提供 baseUrl");
        }
        try {
            client(agentBaseUrl).delete()
                    .uri("/agent/tasks/{taskId}", taskId)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new AgentUnavailableException("取消任务失败: " + e.getMessage(), e);
        }
    }

    private RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(10_000);
        // clone() keeps the Boot-configured message converters and default headers
        // while avoiding shared mutable builder state across concurrent calls.
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
