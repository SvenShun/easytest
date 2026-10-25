package com.testhub.core.feature.apicase;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.core.ApiTestSupport;
import com.testhub.core.feature.plan.ApiExecutionClient;
import com.testhub.core.feature.plan.UiExecutionClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiPlanTriggerTest extends ApiTestSupport {

    @MockBean
    private UiExecutionClient uiExecutionClient;

    @MockBean
    private ApiExecutionClient apiExecutionClient;

    @Test
    void mountsAndTriggersApiCaseTogetherWithUiCase() throws Exception {
        String token = testerToken();
        String uiCase = createApprovedCase(token, "UI 登录用例");
        String apiCase = createApiCase(token, "健康检查接口");

        String planId = createPlan(token, "API + UI 混合计划");
        Map<String, Object> mountBody = new LinkedHashMap<>();
        mountBody.put("caseIds", List.of(uiCase));
        mountBody.put("apiCaseIds", List.of(apiCase));
        mockMvc.perform(post("/api/v1/plans/{id}/cases", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mountBody)))
                .andExpect(status().isOk());

        JsonNode detail = getJson("/api/v1/plans/" + planId, token);
        assertThat(detail.path("cases")).hasSize(2);
        JsonNode apiMount = detail.path("cases").get(1);
        assertThat(apiMount.path("caseType").asText()).isEqualTo("API");
        assertThat(apiMount.path("method").asText()).isEqualTo("GET");
        assertThat(apiMount.path("defaultExecutor").asText()).isEqualTo("API");

        when(uiExecutionClient.createTask(anyMap()))
                .thenReturn(new UiExecutionClient.TaskCreated("ui-task-1", "PENDING", "AI_RUNTIME", "device-1"));
        when(apiExecutionClient.createTask(anyMap()))
                .thenReturn(new ApiExecutionClient.TaskCreated("api-task-1", "PENDING"));

        MvcResult trigger = mockMvc.perform(post("/api/v1/plans/{id}/trigger", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutSec\":60,\"stepTimeoutSec\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].taskId").value("api-task-1"))
                .andExpect(jsonPath("$.items[1].executorType").value("API"))
                .andReturn();
        String runId = json(trigger).path("runId").asText();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiExecutionClient).createTask(captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("planRunId")).isEqualTo(runId);
        assertThat(payload.get("caseId")).isEqualTo(apiCase);
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) payload.get("request");
        assertThat(request.get("method")).isEqualTo("GET");
        assertThat(request.get("path")).isEqualTo("/actuator/health");
        assertThat(request.get("timeoutSec")).isEqualTo(30);

        String resultBody = """
                {"planRunId":"%s","caseId":"%s","taskId":"api-task-1","status":"SUCCESS",
                 "executorType":"API","failureReason":null,"failureReportUrl":null,"summary":"1/1 assertions passed"}
                """.formatted(runId, apiCase);
        mockMvc.perform(post("/internal/v1/plan-runs/items/result")
                        .header("X-Service-Token", "testhub-dev-service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultBody))
                .andExpect(status().isNoContent());

        JsonNode run = getJson("/api/v1/plan-runs/" + runId, token);
        assertThat(run.path("items").get(1).path("status").asText()).isEqualTo("SUCCESS");
        assertThat(run.path("items").get(1).path("executorType").asText()).isEqualTo("API");
    }

    @Test
    void rejectsDisabledApiCaseMount() throws Exception {
        String token = testerToken();
        String apiCase = createApiCase(token, "禁用接口");
        Map<String, Object> update = apiCaseBody("禁用接口");
        update.put("enabled", false);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/api-cases/{id}", apiCase)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        String planId = createPlan(token, "禁用接口计划");
        Map<String, Object> mountBody = new LinkedHashMap<>();
        mountBody.put("apiCaseIds", List.of(apiCase));
        mockMvc.perform(post("/api/v1/plans/{id}/cases", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mountBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_DISABLED"));
    }

    @Test
    void apiCaseCrudAndValidation() throws Exception {
        String token = testerToken();
        String id = createApiCase(token, "登录接口");
        JsonNode listed = getJson("/api/v1/api-cases?keyword=登录&size=10", token);
        assertThat(listed.path("items")).hasSize(1);
        assertThat(listed.path("items").get(0).path("assertions")).hasSize(2);

        Map<String, Object> invalid = apiCaseBody("非法方法");
        invalid.put("method", "TRACE");
        mockMvc.perform(post("/api/v1/api-cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/api-cases/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/api-cases/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolvesEnvironmentAndRunsDatasetIterations() throws Exception {
        String token = testerToken();
        Map<String, Object> envBody = new LinkedHashMap<>();
        envBody.put("name", "数据驱动环境");
        envBody.put("baseUrl", "${host}");
        envBody.put("headers", Map.of("X-Env", "env-${hostName}"));
        envBody.put("variables", Map.of("host", "http://env.local", "hostName", "demo"));
        envBody.put("auth", Map.of("type", "NONE"));
        envBody.put("enabled", true);
        String envId = callJson(post("/api/v1/api-environments"), token, envBody).path("id").asText();

        Map<String, Object> caseBody = apiCaseBody("数据驱动接口");
        caseBody.put("baseUrl", null);
        caseBody.put("path", "/users/${id}");
        caseBody.put("query", Map.of("q", "${q}"));
        caseBody.put("headers", Map.of("X-Case", "row-${id}"));
        caseBody.put("dataset", List.of(Map.of("id", "1", "q", "a"), Map.of("id", "2", "q", "b")));
        String apiCase = callJson(post("/api/v1/api-cases"), token, caseBody).path("id").asText();

        Map<String, Object> planBody = new LinkedHashMap<>();
        planBody.put("name", "数据驱动计划");
        planBody.put("description", "env+dataset");
        planBody.put("environmentId", envId);
        String planId = callJson(post("/api/v1/plans"), token, planBody).path("id").asText();
        Map<String, Object> mountBody = new LinkedHashMap<>();
        mountBody.put("apiCaseIds", List.of(apiCase));
        mockMvc.perform(post("/api/v1/plans/{id}/cases", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mountBody)))
                .andExpect(status().isOk());

        when(apiExecutionClient.createTask(anyMap()))
                .thenReturn(new ApiExecutionClient.TaskCreated("api-row-1", "PENDING"))
                .thenReturn(new ApiExecutionClient.TaskCreated("api-row-2", "PENDING"));

        MvcResult trigger = mockMvc.perform(post("/api/v1/plans/{id}/trigger", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutSec\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].caseTitle").value("数据驱动接口 [1/2]"))
                .andExpect(jsonPath("$.items[1].caseTitle").value("数据驱动接口 [2/2]"))
                .andReturn();
        String runId = json(trigger).path("runId").asText();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiExecutionClient, org.mockito.Mockito.times(2)).createTask(captor.capture());
        Map<String, Object> first = captor.getAllValues().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) first.get("request");
        assertThat(request.get("baseUrl")).isEqualTo("http://env.local");
        assertThat(request.get("path")).isEqualTo("/users/1");
        @SuppressWarnings("unchecked")
        Map<String, String> query = (Map<String, String>) request.get("query");
        assertThat(query.get("q")).isEqualTo("a");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) request.get("headers");
        assertThat(headers).containsEntry("X-Env", "env-demo");
        assertThat(headers).containsEntry("X-Case", "row-1");

        Map<String, Object> second = captor.getAllValues().get(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> secondRequest = (Map<String, Object>) second.get("request");
        assertThat(secondRequest.get("path")).isEqualTo("/users/2");

        JsonNode run = getJson("/api/v1/plan-runs/" + runId, token);
        assertThat(run.path("items")).hasSize(2);
        assertThat(run.path("items").get(0).path("iteration").asInt()).isEqualTo(1);
        assertThat(run.path("items").get(1).path("iteration").asInt()).isEqualTo(2);
    }

    private JsonNode callJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                              String token, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(builder.header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private String createApiCase(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/api-cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiCaseBody(name))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("id").asText();
    }

    private Map<String, Object> apiCaseBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("method", "GET");
        body.put("baseUrl", "http://127.0.0.1:8080");
        body.put("path", "/actuator/health");
        body.put("headers", Map.of("Accept", "application/json"));
        body.put("query", Map.of());
        body.put("body", null);
        body.put("assertions", List.of(
                Map.of("type", "STATUS_CODE", "operator", "EQ", "expected", "200"),
                Map.of("type", "JSON_PATH", "path", "$.status", "operator", "EQ", "expected", "UP")));
        body.put("timeoutSec", 30);
        body.put("enabled", true);
        return body;
    }
}
