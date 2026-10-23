package com.testhub.core.feature.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.common.web.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** RestClient implementation of the ui-execution port. */
@Component
public class RestUiExecutionClient implements UiExecutionClient {

    private final RestClient client;

    public RestUiExecutionClient(RestClient uiExecutionRestClient) {
        this.client = uiExecutionRestClient;
    }

    @Override
    public TaskCreated createTask(Map<String, Object> payload) {
        JsonNode response = client.post()
                .uri("/api/v1/ui-execution/tasks")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || text(response, "taskId") == null) {
            throw ApiException.internal("ui-execution 未返回 taskId");
        }
        return new TaskCreated(
                text(response, "taskId"),
                textOrDefault(response, "status", "PENDING"),
                text(response, "executorType"),
                text(response, "deviceId"));
    }

    @Override
    public List<TaskSnapshot> listTasksByPlanRun(String planRunId) {
        JsonNode response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/ui-execution/tasks")
                        .queryParam("planRunId", planRunId)
                        .queryParam("page", 1)
                        .queryParam("size", 200)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        List<TaskSnapshot> tasks = new ArrayList<>();
        if (response == null) {
            return tasks;
        }
        JsonNode items = response.isArray() ? response : response.path("items");
        if (items == null || !items.isArray()) {
            return tasks;
        }
        for (JsonNode item : items) {
            tasks.add(new TaskSnapshot(
                    text(item, "taskId"),
                    text(item, "status"),
                    text(item, "executorType"),
                    text(item, "deviceId"),
                    text(item, "failureReason"),
                    text(item, "failureReportUrl"),
                    firstNonNull(text(item, "summary"), text(item, "failureSummary"))));
        }
        return tasks;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String textOrDefault(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null ? fallback : value;
    }

    private String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }
}
