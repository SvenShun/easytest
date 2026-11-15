package com.easytest.core.feature.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.easytest.common.security.SecurityProperties;
import com.easytest.common.web.ApiException;
import com.easytest.core.config.CoreProperties;
import com.easytest.core.feature.apiagent.ApiAgentRegistry;
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
    private final ApiAgentRegistry agentRegistry;
    private final int agentWaitSeconds;
    private final AtomicInteger cursor = new AtomicInteger();

    public RestApiExecutionClient(CoreProperties coreProperties, SecurityProperties securityProperties,
                                  ApiAgentRegistry agentRegistry) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("X-Service-Token", securityProperties.getServiceToken())
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.agentRegistry = agentRegistry;
        this.agentWaitSeconds = Math.max(0, coreProperties.getApiAgentWaitSeconds());
        String raw = coreProperties.getApiExecutionBaseUrls();
        this.baseUrls = Arrays.stream((raw == null || raw.isBlank() ? "http://127.0.0.1:8084" : raw).split(","))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .map(RestApiExecutionClient::trimTrailingSlash)
                .toList();
    }

    @Override
    public TaskCreated createTask(Map<String, Object> payload) {
        java.util.Set<String> requiredCapabilities = requiredCapabilities(payload);
        if (agentRegistry != null && agentRegistry.hasAgents()) {
            return createTaskViaAgents(payload, requiredCapabilities);
        }
        return createTaskViaBaseUrls(payload, baseUrls);
    }

    private TaskCreated createTaskViaAgents(Map<String, Object> payload, java.util.Set<String> requiredCapabilities) {
        long deadline = System.currentTimeMillis() + agentWaitSeconds * 1000L;
        RuntimeException last = null;
        while (System.currentTimeMillis() <= deadline) {
            List<String> available = agentRegistry.availableBaseUrls(requiredCapabilities);
            for (String baseUrl : available) {
                if (!agentRegistry.reserveAt(baseUrl)) continue;
                try {
                    return postTask(baseUrl, payload);
                } catch (RuntimeException e) {
                    last = e;
                    agentRegistry.release(baseUrl);
                    log.warn("api-execution 任务下发失败，尝试故障转移 worker={}: {}", baseUrl, e.getMessage());
                }
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw last == null ? ApiException.conflict("没有可用的 API Agent（能力/槽位不足）") : last;
    }

    private TaskCreated createTaskViaBaseUrls(Map<String, Object> payload, List<String> candidates) {
        if (candidates.isEmpty()) {
            throw ApiException.conflict("没有可用的 API 执行实例");
        }
        RuntimeException last = null;
        int start = Math.floorMod(cursor.getAndIncrement(), candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            String baseUrl = candidates.get((start + i) % candidates.size());
            try {
                return postTask(baseUrl, payload);
            } catch (RuntimeException e) {
                last = e;
                log.warn("api-execution 任务下发失败 worker={}: {}", baseUrl, e.getMessage());
            }
        }
        throw last == null ? ApiException.internal("没有可用的 api-execution 实例") : last;
    }

    private TaskCreated postTask(String baseUrl, Map<String, Object> payload) {
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
    }

    @Override
    public List<TaskSnapshot> listTasksByPlanRun(String planRunId) {
        List<TaskSnapshot> tasks = new ArrayList<>();
        RuntimeException last = null;
        boolean anySuccess = false;
        for (String baseUrl : candidateUrls(java.util.Set.of())) {
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

    @SuppressWarnings("unchecked")
    private java.util.Set<String> requiredCapabilities(Map<String, Object> payload) {
        Object value = payload == null ? null : payload.get("requiredCapabilities");
        if (value instanceof java.util.Collection<?> collection) {
            java.util.Set<String> result = new java.util.LinkedHashSet<>();
            collection.forEach(item -> {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item).toUpperCase(java.util.Locale.ROOT));
                }
            });
            return result;
        }
        return java.util.Set.of();
    }

    private List<String> candidateUrls(java.util.Set<String> requiredCapabilities) {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        if (agentRegistry != null && agentRegistry.hasAgents()) {
            candidates.addAll(agentRegistry.availableBaseUrls(requiredCapabilities));
            return new ArrayList<>(candidates);
        }
        candidates.addAll(baseUrls);
        return new ArrayList<>(candidates);
    }

    @Override
    public List<EngineeringMethod> listEngineeringMethods() {
        java.util.LinkedHashMap<String, EngineeringMethod> methods = new java.util.LinkedHashMap<>();
        for (String baseUrl : candidateUrls(java.util.Set.of())) {
            try {
                JsonNode response = client.get()
                        .uri(baseUrl + "/api/v1/api-execution/contracts")
                        .retrieve()
                        .body(JsonNode.class);
                JsonNode data = unwrap(response);
                JsonNode items = data != null && data.isArray() ? data : data == null ? null : data.path("items");
                if (items == null || !items.isArray()) continue;
                for (JsonNode item : items) {
                    String path = text(item, "methodPath");
                    if (path == null) continue;
                    methods.put(path, new EngineeringMethod(text(item, "id"), text(item, "name"),
                            text(item, "description"), path, texts(item, "inputs"),
                            texts(item, "outputs"), texts(item, "tags"), text(item, "layer"),
                            texts(item, "protocols"), text(item, "inputSchema"),
                            text(item, "outputSchema")));
                }
            } catch (RuntimeException e) {
                log.warn("engineering methods query failed worker={}: {}", baseUrl, e.getMessage());
            }
        }
        return new ArrayList<>(methods.values());
    }

    @Override
    public void uploadEngineeringArtifact(String filename, byte[] content) {
        RuntimeException last = null;
        for (String baseUrl : candidateUrls(java.util.Set.of("COMPONENT"))) {
            try {
                org.springframework.core.io.ByteArrayResource resource =
                        new org.springframework.core.io.ByteArrayResource(content) {
                            @Override
                            public String getFilename() {
                                return filename;
                            }
                        };
                org.springframework.util.LinkedMultiValueMap<String, Object> parts =
                        new org.springframework.util.LinkedMultiValueMap<>();
                parts.add("file", resource);
                client.post()
                        .uri(baseUrl + "/api/v1/api-execution/engineering/upload")
                        .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                        .body(parts)
                        .retrieve()
                        .body(JsonNode.class);
                log.info("engineering artifact uploaded worker={} file={}", baseUrl, filename);
                return;
            } catch (RuntimeException e) {
                last = e;
                log.warn("engineering artifact upload failed worker={}: {}", baseUrl, e.getMessage());
            }
        }
        throw last == null ? ApiException.conflict("没有可用的 API Agent 部署工程 JAR") : last;
    }

    private List<String> texts(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        value.forEach(item -> result.add(item.asText()));
        return result;
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
