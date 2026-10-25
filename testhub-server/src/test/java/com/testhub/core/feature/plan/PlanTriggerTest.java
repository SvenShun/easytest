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

}
