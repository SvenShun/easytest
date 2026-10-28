package com.testhub.core.feature.apiscenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.common.security.SecurityProperties;
import com.testhub.common.web.ApiException;
import com.testhub.core.config.CoreProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.Map;

@Component
public class RestScenarioExecutionClient implements ScenarioExecutionClient {

    private final RestClient client;
    private final String baseUrl;

    public RestScenarioExecutionClient(CoreProperties coreProperties, SecurityProperties securityProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(90_000);
        this.client = RestClient.builder().requestFactory(factory)
                .defaultHeader("X-Service-Token", securityProperties.getServiceToken())
                .defaultHeader("Content-Type", "application/json")
                .build();
        String raw = coreProperties.getApiExecutionBaseUrls();
        this.baseUrl = Arrays.stream((raw == null || raw.isBlank() ? "http://127.0.0.1:8084" : raw).split(","))
                .map(String::trim).filter(item -> !item.isEmpty())
                .map(item -> item.endsWith("/") ? item.substring(0, item.length() - 1) : item)
                .findFirst().orElse("http://127.0.0.1:8084");
    }

    @Override
    public JsonNode create(Map<String, Object> payload) {
        try {
            return unwrap(client.post().uri(baseUrl + "/api/v1/api-execution/scenarios")
                    .body(payload).retrieve().body(JsonNode.class));
        } catch (RuntimeException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "SCENARIO_DISPATCH_FAILED",
                    "场景任务下发失败: " + e.getMessage());
        }
    }

    @Override
    public JsonNode detail(String taskId) {
        try {
            return unwrap(client.get().uri(baseUrl + "/api/v1/api-execution/scenarios/{id}", taskId)
                    .retrieve().body(JsonNode.class));
        } catch (RuntimeException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "SCENARIO_READ_FAILED",
                    "读取场景任务失败: " + e.getMessage());
        }
    }

    @Override
    public JsonNode validate(String yaml) {
        try {
            return unwrap(client.post().uri(baseUrl + "/api/v1/api-execution/scenarios/validate")
                    .body(Map.of("yaml", yaml == null ? "" : yaml)).retrieve().body(JsonNode.class));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("YAML 校验失败: " + e.getMessage());
        }
    }

    private JsonNode unwrap(JsonNode response) {
        if (response != null && response.has("data") && !response.get("data").isNull()) {
            return response.get("data");
        }
        return response;
    }
}
