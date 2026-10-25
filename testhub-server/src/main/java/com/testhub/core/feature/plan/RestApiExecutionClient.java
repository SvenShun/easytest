package com.testhub.core.feature.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.common.security.SecurityProperties;
import com.testhub.common.web.ApiException;
import com.testhub.core.config.CoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RestClient implementation of the api-execution port. Tasks are dispatched
 * round-robin across all configured worker URLs; reconciliation merges results
 * from every worker.
 */
@Component
public class RestApiExecutionClient implements ApiExecutionClient {

    private static final Logger log = LoggerFactory.getLogger(RestApiExecutionClient.class);

    private final RestClient client;
    private final List<String> baseUrls;
    private final AtomicInteger cursor = new AtomicInteger();

    public RestApiExecutionClient(CoreProperties coreProperties, SecurityProperties securityProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("X-Service-Token", securityProperties.getServiceToken())
                .defaultHeader("Content-Type", "application/json")
                .build();
        String raw = coreProperties.getApiExecutionBaseUrls();
        this.baseUrls = Arrays.stream((raw == null || raw.isBlank() ? "http://127.0.0.1:8084" : raw).split(","))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .map(RestApiExecutionClient::trimTrailingSlash)
                .toList();
    }

    @Override
    public TaskCreated createTask(Map<String, Object> payload) {
        RuntimeException last = null;
        int start = Math.floorMod(cursor.getAndIncrement(), baseUrls.size());
        for (int i = 0; i < baseUrls.size(); i++) {
            String baseUrl = baseUrls.get((start + i) % baseUrls.size());
            try {
                JsonNode response = client.post()
                        .uri(baseUrl + "/api/v1/api-execution/tasks")
                        .body(payload)
                        .retrieve()
                        .body(JsonNode.class);
                JsonNode data = unwrap(response);
                String taskId = text(data, "taskId");
                if (taskId == null) {
                    throw ApiException.internal("api-execution 未返回 taskId");
                }
                return new TaskCreated(taskId, textOrDefault(data, "status", "PENDING"));
            } catch (RuntimeException e) {
                last = e;
                log.warn("api-execution 任务下发失败 worker={}: {}", baseUrl, e.getMessage());
            }
        }
        throw last == null ? ApiException.internal("没有可用的 api-execution 实例") : last;
    }

    @Override
    public List<TaskSnapshot> listTasksByPlanRun(String planRunId) {
        List<TaskSnapshot> tasks = new ArrayList<>();
        RuntimeException last = null;
        boolean anySuccess = false;
        for (String baseUrl : baseUrls) {
            try {
                JsonNode response = client.get()
                        .uri(UriComponentsBuilder.fromHttpUrl(baseUrl)
                                .path("/api/v1/api-execution/tasks")
                                .queryParam("planRunId", planRunId)
                                .queryParam("page", 1)
                                .queryParam("size", 200)
                                .build(true)
                                .toUri())
                        .retrieve()
                        .body(JsonNode.class);
                anySuccess = true;
                JsonNode data = unwrap(response);
                JsonNode items = data != null && data.isArray() ? data : data == null ? null : data.path("items");
                if (items == null || !items.isArray()) {
                    continue;
                }
                for (JsonNode item : items) {
                    tasks.add(new TaskSnapshot(text(item, "taskId"), text(item, "status"),
                            text(item, "failureReason"), text(item, "failureReportUrl"),
                            text(item, "summary")));
                }
            } catch (RuntimeException e) {
                last = e;
                log.warn("api-execution 对账失败 worker={}: {}", baseUrl, e.getMessage());
            }
        }
        if (!anySuccess && last != null) {
            throw last;
        }
        return tasks;
    }

    private JsonNode unwrap(JsonNode response) {
        if (response != null && response.has("data") && !response.get("data").isNull()) {
            return response.get("data");
        }
        return response;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null ? fallback : value;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
