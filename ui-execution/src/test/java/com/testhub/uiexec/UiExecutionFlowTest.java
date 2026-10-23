package com.testhub.uiexec;

import com.testhub.uiexec.client.AgentClient;
import com.testhub.uiexec.client.AgentUnavailableException;
import com.testhub.uiexec.client.CoreClient;
import com.testhub.uiexec.client.dto.ExecutionContext;
import com.testhub.uiexec.client.dto.FailureAnalysisPayload;
import com.testhub.uiexec.client.dto.ModelCredential;
import com.testhub.uiexec.client.dto.PlanItemResultPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end wiring test: agent registration → task creation/routing/dispatch →
 * step/log/result callbacks → failure report + core write-backs. The core and
 * agent clients are mocked so no external process is needed.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:uiexec-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "testhub.uiexec.core-base-url=http://127.0.0.1:1",
        "testhub.uiexec.watchdog-enabled=false",
        "testhub.uiexec.artifact-dir=./target/test-artifacts-it"
})
@AutoConfigureMockMvc
class UiExecutionFlowTest {

    private static final String AGENT_TOKEN = "testhub-dev-agent-token";
    private static final String SERVICE_TOKEN = "testhub-dev-service-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CoreClient coreClient;

    @MockBean
    private AgentClient agentClient;

    @Test
    void fullTaskLifecycleCreatesReportAndWritesBack() throws Exception {
        when(coreClient.fetchExecutionContext("case-it-1")).thenReturn(new ExecutionContext(
                "case-it-1", "用户使用正确凭据登录", "APPROVED",
                new ExecutionContext.Bdd("已打开 App", "输入用户名密码并登录", "进入首页"),
                null, null));
        when(coreClient.fetchVisionCredential()).thenReturn(Optional.of(
                new ModelCredential("vision", "mock", "", "", "mock-vision", null)));

        // 1. register agent + device (agent token, manually verified as well)
        MvcResult registerResult = mockMvc.perform(post("/api/v1/ui-execution/agents/register")
                        .header("X-Agent-Token", AGENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"agent-it","host":"localhost","baseUrl":"http://127.0.0.1:8090",
                                 "devices":[{"serial":"emulator-5554","name":"Pixel 7",
                                             "model":"sdk_gphone64","platform":"android",
                                             "tags":["android","pixel"],"online":true}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").isNotEmpty())
                .andExpect(jsonPath("$.devices[0].serial").value("emulator-5554"))
                .andReturn();
        String agentId = Json.read(registerResult, "agentId");
        assertThat(agentId).isNotBlank();

        // 2. create task as a service caller (service token path)
        MvcResult createResult = mockMvc.perform(post("/api/v1/ui-execution/tasks")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planId":"plan-it","planRunId":"run-it","caseId":"case-it-1",
                                 "executorType":null,
                                 "targetDeviceSelector":{"deviceId":null,"agentId":null,"tags":["android"]},
                                 "timeoutSec":120,"stepTimeoutSec":30,"createdBy":"tester"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.executorType").value("AI_RUNTIME"))
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.deviceSerial").value("emulator-5554"))
                .andReturn();
        String taskId = Json.read(createResult, "taskId");
        assertThat(taskId).isNotBlank();
        verify(agentClient).dispatch(eq("http://127.0.0.1:8090"), any());

        // 3. agent reports a step with a screenshot → task becomes RUNNING
        mockMvc.perform(post("/api/v1/ui-execution/tasks/" + taskId + "/steps")
                        .header("X-Agent-Token", AGENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seq":1,"status":"OK","decision":"点击登录按钮",
                                 "action":"click(登录)","elapsedMs":1200,
                                 "screenshotBase64":"ZmFrZS1wbmc=",
                                 "screenshotContentType":"image/png"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stepId").isNotEmpty())
                .andExpect(jsonPath("$.screenshotUrl").value(
                        org.hamcrest.Matchers.containsString("/artifacts/" + taskId + "/steps/1.png")));

        mockMvc.perform(get("/api/v1/ui-execution/tasks/" + taskId)
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());

        // 4. logs are appended and queryable
        mockMvc.perform(post("/api/v1/ui-execution/tasks/" + taskId + "/logs")
                        .header("X-Agent-Token", AGENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lines":[{"level":"INFO","message":"开始执行"},
                                          {"level":"WARN","message":"等待元素"}]}
                                """))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/ui-execution/tasks/" + taskId + "/logs")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .param("afterSeq", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].seq").value(1));

        // 5. result callback with failure category + artifact
        when(coreClient.fetchExecutionContext("case-it-1")).thenReturn(new ExecutionContext(
                "case-it-1", "用户使用正确凭据登录", "APPROVED",
                new ExecutionContext.Bdd("已打开 App", "输入用户名密码并登录", "进入首页"),
                null, null));
        mockMvc.perform(post("/api/v1/ui-execution/tasks/" + taskId + "/result")
                        .header("X-Agent-Token", AGENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"FAILED","failureReason":"MODEL_DECISION_FAILED",
                                 "summary":"第 4 步模型输出无法解析",
                                 "reasonCategory":"MODEL_DECISION_FAILED",
                                 "artifacts":[{"name":"junit.xml","type":"JUNIT",
                                               "contentBase64":"PGp1bml0Lz4="}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReportUrl").isNotEmpty());

        // 6. detail exposes steps/logs/artifacts/failure report
        mockMvc.perform(get("/api/v1/ui-execution/tasks/" + taskId)
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("MODEL_DECISION_FAILED"))
                .andExpect(jsonPath("$.failureReportUrl").isNotEmpty())
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.logs.length()").value(2))
                .andExpect(jsonPath("$.artifacts[0].name").value("junit.xml"))
                .andExpect(jsonPath("$.artifacts[0].type").value("JUNIT"))
                .andExpect(jsonPath("$.failureReport.reasonCategory").value("MODEL_DECISION_FAILED"))
                .andExpect(jsonPath("$.skills").doesNotExist());

        // 7. core write-backs happened (failure analysis + plan item result)
        verify(coreClient).updateFailureAnalysis(eq("case-it-1"), any(FailureAnalysisPayload.class));
        verify(coreClient).postPlanItemResult(any(PlanItemResultPayload.class));

        // 8. duplicate result callback is rejected
        mockMvc.perform(post("/api/v1/ui-execution/tasks/" + taskId + "/result")
                        .header("X-Agent-Token", AGENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUCCESS"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_FINISHED"));

        // 9. device is released and stats/queue endpoints work
        mockMvc.perform(get("/api/v1/ui-execution/devices")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].currentTaskId").doesNotExist());

        mockMvc.perform(get("/api/v1/ui-execution/devices/" + agentDeviceId() + "/queue")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueLength").value(0));

        mockMvc.perform(get("/api/v1/ui-execution/stats")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.failed", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.failureCategories[?(@.category == 'MODEL_DECISION_FAILED')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/ui-execution/agents")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agentId").value(agentId));

        cleanupArtifacts(taskId);
    }

    @Test
    void createTaskForUnapprovedCaseIsRejectedWith409() throws Exception {
        when(coreClient.fetchExecutionContext("case-it-2")).thenReturn(new ExecutionContext(
                "case-it-2", "未评审用例", "DRAFT",
                new ExecutionContext.Bdd("g", "w", "t"), null, null));

        mockMvc.perform(post("/api/v1/ui-execution/tasks")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseId":"case-it-2","timeoutSec":60,"stepTimeoutSec":10}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_NOT_APPROVED"));
    }

    @Test
    void agentCallbackWithoutAgentTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/ui-execution/tasks/does-not-exist/steps")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seq":1,"status":"OK"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_AGENT_TOKEN"));
    }

    @Test
    void queueingHeartbeatAndCancellationWork() throws Exception {
        when(coreClient.fetchExecutionContext(anyString())).thenReturn(new ExecutionContext(
                "case-cancel", "取消与排队用例", "APPROVED",
                new ExecutionContext.Bdd("g", "w", "t"), null, null));
        when(coreClient.fetchVisionCredential()).thenReturn(Optional.of(
                new ModelCredential("vision", "mock", "", "", "mock-vision", null)));

        // idempotent re-registration (the same agent name is used by the lifecycle test)
        MvcResult registerResult = mockMvc.perform(post("/api/v1/ui-execution/agents/register")
                        .header("X-Agent-Token", AGENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"agent-it","host":"localhost","baseUrl":"http://127.0.0.1:8090",
                                 "devices":[{"serial":"emulator-5554","name":"Pixel 7",
                                             "model":"sdk_gphone64","platform":"android",
                                             "tags":["android","pixel"],"online":true}]}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String agentId = Json.read(registerResult, "agentId");

        mockMvc.perform(post("/api/v1/ui-execution/agents/" + agentId + "/heartbeat")
                        .header("X-Agent-Token", AGENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"devices":[{"serial":"emulator-5554","online":true,
                                             "tags":["android","pixel"],"platform":"android"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(agentId))
                .andExpect(jsonPath("$.status").value("ONLINE"))
                .andExpect(jsonPath("$.devices[0].online").value(true));

        // first task occupies the only device
        String taskA = createTask("case-cancel");
        mockMvc.perform(get("/api/v1/ui-execution/tasks/" + taskA)
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        // second task is queued behind it → PENDING with position 1
        MvcResult second = mockMvc.perform(post("/api/v1/ui-execution/tasks")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseId":"case-cancel","targetDeviceSelector":{"tags":["android"]},
                                 "timeoutSec":120,"stepTimeoutSec":30}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andReturn();
        String taskB = Json.read(second, "taskId");

        String deviceId = agentDeviceId();
        mockMvc.perform(get("/api/v1/ui-execution/devices/" + deviceId + "/queue")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueLength").value(1))
                .andExpect(jsonPath("$.tasks[0].taskId").value(taskB))
                .andExpect(jsonPath("$.tasks[0].position").value(1));

        // cancel a queued task: removed from queue, CANCELLED with manual interrupt
        mockMvc.perform(post("/api/v1/ui-execution/tasks/" + taskB + "/cancel")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/v1/ui-execution/devices/" + deviceId + "/queue")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueLength").value(0));

        // cancel a dispatched task while the agent is unreachable → direct CANCELLED
        doThrow(new AgentUnavailableException("agent down")).when(agentClient).cancel(anyString(), eq(taskA));
        mockMvc.perform(post("/api/v1/ui-execution/tasks/" + taskA + "/cancel")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(agentClient).cancel("http://127.0.0.1:8090", taskA);
        mockMvc.perform(get("/api/v1/ui-execution/devices")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("IDLE"))
                .andExpect(jsonPath("$.items[0].currentTaskId").doesNotExist());
    }

    private String createTask(String caseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ui-execution/tasks")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseId":"%s","timeoutSec":120,"stepTimeoutSec":30}
                                """.formatted(caseId)))
                .andExpect(status().isCreated())
                .andReturn();
        return Json.read(result, "taskId");
    }

    private String agentDeviceId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/ui-execution/devices")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        return Json.read(result, "items", 0, "deviceId");
    }

    private void cleanupArtifacts(String taskId) {
        // best effort: keep ./target tidy, failure of cleanup is irrelevant
        try {
            java.nio.file.Path root = java.nio.file.Paths.get("./target/test-artifacts-it").toAbsolutePath()
                    .normalize();
            java.nio.file.Path taskDir = root.resolve(taskId).normalize();
            if (taskDir.startsWith(root) && java.nio.file.Files.exists(taskDir)) {
                try (var paths = java.nio.file.Files.walk(taskDir)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try {
                            java.nio.file.Files.deleteIfExists(path);
                        } catch (java.io.IOException ignored) {
                            // ignore
                        }
                    });
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    /** Tiny JSON path reader to avoid extra test dependencies. */
    private static final class Json {

        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();

        private Json() {
        }

        private static String read(MvcResult result, String field) throws Exception {
            return MAPPER.readTree(result.getResponse().getContentAsByteArray()).path(field).asText();
        }

        private static String read(MvcResult result, String arrayField, int index, String field) throws Exception {
            return MAPPER.readTree(result.getResponse().getContentAsByteArray())
                    .path(arrayField).path(index).path(field).asText();
        }
    }
}
