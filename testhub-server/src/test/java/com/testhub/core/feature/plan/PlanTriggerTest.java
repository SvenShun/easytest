package com.testhub.core.feature.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.common.util.Ids;
import com.testhub.core.ApiTestSupport;
import com.testhub.core.feature.script.ScriptProject;
import com.testhub.core.feature.script.ScriptProjectRepository;
import com.testhub.core.feature.script.ScriptType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanTriggerTest extends ApiTestSupport {

    @MockBean
    private UiExecutionClient uiExecutionClient;

    @MockBean
    private ApiExecutionClient apiExecutionClient;

    @Autowired
    private ScriptProjectRepository scriptRepository;

    @Autowired
    private PlanCaseRepository planCaseRepository;

    @Test
    void triggerCreatesItemsAndContinuesWhenUiExecutionFails() throws Exception {
        String token = testerToken();
        ScriptProject script = scriptRepository.save(new ScriptProject("script-routing", "login.zip",
                ScriptType.TESTNG, "sh run.sh", "scripts/script-routing.zip", 512, "tester", Instant.now()));
        String caseWithScript = createCase(token, "有脚本且已通过用例", script.getId());
        review(token, caseWithScript, "SUBMIT");
        review(token, caseWithScript, "APPROVE");
        String noScript = createApprovedCase(token, "无脚本用例");

        String planId = createPlan(token, "混合执行计划");
        mount(token, planId, List.of(caseWithScript, noScript), "AI_RUNTIME");

        JsonNode planDetail = getJson("/api/v1/plans/" + planId, token);
        assertThat(planDetail.path("cases")).hasSize(2);
        assertThat(planDetail.path("cases").get(0).path("hasScript").asBoolean()).isTrue();
        assertThat(planDetail.path("cases").get(0).path("defaultExecutor").asText()).isEqualTo("SCRIPT");
        assertThat(planDetail.path("cases").get(0).path("executorTypeOverride").asText())
                .isEqualTo("AI_RUNTIME");
        assertThat(planDetail.path("cases").get(1).path("hasScript").asBoolean()).isFalse();

        when(uiExecutionClient.createTask(anyMap()))
                .thenReturn(new UiExecutionClient.TaskCreated("task-1", "PENDING", "AI_RUNTIME", "device-1"))
                .thenThrow(new RuntimeException("ui-execution unreachable"));

        String triggerBody = """
                {"targetDeviceId":null,"targetTags":["android"],"timeoutSec":90,"stepTimeoutSec":20}
                """;
        MvcResult triggerResult = mockMvc.perform(post("/api/v1/plans/{id}/trigger", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(triggerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].taskId").value("task-1"))
                .andExpect(jsonPath("$.items[0].taskStatus").value("PENDING"))
                .andExpect(jsonPath("$.items[0].executorType").value("AI_RUNTIME"))
                .andExpect(jsonPath("$.items[1].taskStatus").value("FAILED"))
                .andReturn();
        String runId = json(triggerResult).path("runId").asText();
        assertThat(json(triggerResult).path("items").get(1).path("error").asText())
                .contains("ui-execution unreachable");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(uiExecutionClient, times(2)).createTask(captor.capture());
        Map<String, Object> first = captor.getAllValues().get(0);
        assertThat(first.get("planId")).isEqualTo(planId);
        assertThat(first.get("planRunId")).isEqualTo(runId);
        assertThat(first.get("caseId")).isEqualTo(caseWithScript);
        assertThat(first.get("executorType")).isEqualTo("AI_RUNTIME");
        assertThat(first.get("timeoutSec")).isEqualTo(90);
        assertThat(first.get("stepTimeoutSec")).isEqualTo(20);
        assertThat(first.get("createdBy")).isEqualTo("tester");
        @SuppressWarnings("unchecked")
        Map<String, Object> selector = (Map<String, Object>) first.get("targetDeviceSelector");
        assertThat(selector.get("tags")).isEqualTo(List.of("android"));
        assertThat(selector.get("deviceId")).isNull();

        String resultBody = """
                {"planRunId":"%s","caseId":"%s","taskId":"task-1","status":"SUCCESS",
                 "executorType":"AI_RUNTIME","failureReason":null,"failureReportUrl":null,"summary":"全部步骤通过"}
                """.formatted(runId, caseWithScript);
        mockMvc.perform(post("/internal/v1/plan-runs/items/result")
                        .header("X-Service-Token", "testhub-dev-service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultBody))
                .andExpect(status().isNoContent());

        JsonNode run = getJson("/api/v1/plan-runs/" + runId, token);
        assertThat(run.path("run").path("status").asText()).isEqualTo("PARTIAL");
        assertThat(run.path("run").path("total").asInt()).isEqualTo(2);
        assertThat(run.path("run").path("success").asInt()).isEqualTo(1);
        assertThat(run.path("run").path("failed").asInt()).isEqualTo(1);
        assertThat(run.path("items")).hasSize(2);

        JsonNode report = getJson("/api/v1/plan-runs/" + runId + "/report", token);
        assertThat(report.path("summary").path("total").asInt()).isEqualTo(2);
        assertThat(report.path("summary").path("success").asInt()).isEqualTo(1);
        assertThat(report.path("summary").path("failed").asInt()).isEqualTo(1);
        assertThat(report.path("executors")).hasSize(1);
        assertThat(report.path("executors").get(0).path("executorType").asText()).isEqualTo("AI_RUNTIME");
        assertThat(report.path("executors").get(0).path("total").asInt()).isEqualTo(2);
    }

    @Test
    void nonApprovedMountedCaseIsSkipped() throws Exception {
        String token = testerToken();
        String approved = createApprovedCase(token, "已通过用例");
        String draft = createCase(token, "未通过用例", null);
        String planId = createPlan(token, "跳过用例计划");
        mount(token, planId, List.of(approved), null);
        planCaseRepository.save(new PlanCase(Ids.newId("plan-case"), planId, draft, null,
                Instant.now().plusSeconds(1)));

        when(uiExecutionClient.createTask(anyMap()))
                .thenReturn(new UiExecutionClient.TaskCreated("task-skip", "PENDING", "AI_RUNTIME", "d1"));

        MvcResult triggerResult = mockMvc.perform(post("/api/v1/plans/{id}/trigger", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].caseId").value(draft))
                .andExpect(jsonPath("$.items[1].taskStatus").value("SKIPPED"))
                .andReturn();
        String runId = json(triggerResult).path("runId").asText();
        assertThat(json(triggerResult).path("items").get(1).path("taskId").isNull()).isTrue();

        // Two items: one queued (still pending) => run is RUNNING.
        JsonNode running = getJson("/api/v1/plan-runs/" + runId, token);
        assertThat(running.path("run").path("status").asText()).isEqualTo("RUNNING");
        assertThat(running.path("run").path("pending").asInt()).isEqualTo(1);
        assertThat(running.path("run").path("skipped").asInt()).isEqualTo(1);

        mockMvc.perform(post("/internal/v1/plan-runs/items/result")
                        .header("X-Service-Token", "testhub-dev-service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planRunId":"%s","caseId":"%s","taskId":"task-skip","status":"SUCCESS",
                                 "executorType":"AI_RUNTIME"}
                                """.formatted(runId, approved)))
                .andExpect(status().isNoContent());

        JsonNode completed = getJson("/api/v1/plan-runs/" + runId, token);
        assertThat(completed.path("run").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("run").path("success").asInt()).isEqualTo(1);
        assertThat(completed.path("run").path("skipped").asInt()).isEqualTo(1);
        assertThat(completed.path("run").path("finishedAt").isNull()).isFalse();
    }

    @Test
    void planWithoutCasesReturnsNoExecutableCase() throws Exception {
        String token = testerToken();
        String planId = createPlan(token, "空计划");
        mockMvc.perform(post("/api/v1/plans/{id}/trigger", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_EXECUTABLE_CASE"));
    }

    @Test
    void mountedCaseCannotBeDeleted() throws Exception {
        String token = testerToken();
        String caseId = createApprovedCase(token, "被计划引用用例");
        String planId = createPlan(token, "引用计划");
        mount(token, planId, List.of(caseId), null);
        mockMvc.perform(delete("/api/v1/cases/{id}", caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_IN_USE"));
    }

    @Test
    void stepCaseRoutesToStepExecutorWithStepsAndCaseTitle() throws Exception {
        String token = testerToken();
        String caseId = createStepCase(token, "步骤登录用例");
        review(token, caseId, "SUBMIT");
        review(token, caseId, "APPROVE");
        String planId = createPlan(token, "步骤执行计划");
        mount(token, planId, List.of(caseId), null);

        when(uiExecutionClient.createTask(anyMap()))
                .thenReturn(new UiExecutionClient.TaskCreated("task-step-1", "PENDING", "STEP", "device-step"));

        MvcResult triggerResult = mockMvc.perform(post("/api/v1/plans/{id}/trigger", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutSec\":90,\"stepTimeoutSec\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].executorType").value("STEP"))
                .andExpect(jsonPath("$.items[0].taskStatus").value("PENDING"))
                .andExpect(jsonPath("$.items[0].taskId").value("task-step-1"))
                .andReturn();
        String runId = json(triggerResult).path("runId").asText();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(uiExecutionClient, times(1)).createTask(captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("planId")).isEqualTo(planId);
        assertThat(payload.get("planRunId")).isEqualTo(runId);
        assertThat(payload.get("caseId")).isEqualTo(caseId);
        assertThat(payload.get("caseTitle")).isEqualTo("步骤登录用例");
        assertThat(payload.get("executorType")).isEqualTo("STEP");
        assertThat(payload.get("timeoutSec")).isEqualTo(90);
        assertThat(payload.get("stepTimeoutSec")).isEqualTo(20);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) payload.get("steps");
        assertThat(steps).hasSize(3);
        assertThat(steps.get(0).get("seq")).isEqualTo(1);
        assertThat(steps.get(0).get("stepType")).isEqualTo("CLICK");
        assertThat(steps.get(0).get("selectorType")).isEqualTo("TEXT");
        assertThat(steps.get(0).get("selector")).isEqualTo("登录");
        assertThat(steps.get(1).get("stepType")).isEqualTo("INPUT");
        assertThat(steps.get(1).get("value")).isEqualTo("tester");
        assertThat(steps.get(2).get("stepType")).isEqualTo("ASSERT_TEXT");
        assertThat(steps.get(2).get("assertValue")).isEqualTo("首页");
        assertThat(steps.get(2).get("timeoutMs")).isEqualTo(5000);
        // Disabled steps are not dispatched.
        assertThat(steps).noneMatch(step -> "SCREENSHOT".equals(step.get("stepType")));

        JsonNode planDetail = getJson("/api/v1/plans/" + planId, token);
        assertThat(planDetail.path("cases").get(0).path("defaultExecutor").asText()).isEqualTo("STEP");
        assertThat(planDetail.path("cases").get(0).path("availableExecutors").get(0).asText())
                .isEqualTo("STEP");

        mockMvc.perform(post("/internal/v1/plan-runs/items/result")
                        .header("X-Service-Token", "testhub-dev-service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planRunId":"%s","caseId":"%s","taskId":"task-step-1","status":"SUCCESS",
                                 "executorType":"STEP","summary":"3/3 steps passed"}
                                """.formatted(runId, caseId)))
                .andExpect(status().isNoContent());

        JsonNode run = getJson("/api/v1/plan-runs/" + runId, token);
        assertThat(run.path("run").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(run.path("items").get(0).path("executorType").asText()).isEqualTo("STEP");
        assertThat(run.path("items").get(0).path("summary").asText()).isEqualTo("3/3 steps passed");
    }

    @Test
    void stepCaseRejectsNonStepOverride() throws Exception {
        String token = testerToken();
        String caseId = createStepCase(token, "覆盖冲突步骤用例");
        review(token, caseId, "SUBMIT");
        review(token, caseId, "APPROVE");
        String planId = createPlan(token, "覆盖冲突计划");
        mount(token, planId, List.of(caseId), "AI_RUNTIME");

        mockMvc.perform(post("/api/v1/plans/{id}/trigger", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].taskStatus").value("FAILED"))
                .andExpect(jsonPath("$.items[0].taskId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].error").value(
                        org.hamcrest.Matchers.containsString("只支持 STEP")));

        verify(uiExecutionClient, never()).createTask(anyMap());
    }

    private String createStepCase(String token, String title) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("given", "已打开 App");
        body.put("when", "执行步骤");
        body.put("then", "看到首页");
        body.put("executionMode", "STEPS");
        body.put("steps", List.of(
                step(1, "CLICK", "TEXT", "登录", null, null, null, true),
                step(2, "INPUT", "ID", "username", "tester", null, null, true),
                step(3, "ASSERT_TEXT", "TEXT", "首页", null, "首页", 5000, true),
                step(4, "SCREENSHOT", null, null, null, null, null, false)));
        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("id").asText();
    }

    private Map<String, Object> step(Integer seq, String stepType, String selectorType, String selector,
                                     String value, String assertValue, Integer timeoutMs, Boolean enabled) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("seq", seq);
        step.put("stepType", stepType);
        step.put("selectorType", selectorType);
        step.put("selector", selector);
        step.put("value", value);
        step.put("assertValue", assertValue);
        step.put("timeoutMs", timeoutMs);
        step.put("enabled", enabled);
        return step;
    }

}
