package com.testhub.uiexec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.uiexec.client.AgentClient;
import com.testhub.uiexec.client.CoreClient;
import com.testhub.uiexec.client.dto.ExecutionContext;
import com.testhub.uiexec.service.StepTaskRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wiring test for STEP tasks: no agent {@code /agent/tasks} dispatch, per-device FIFO queueing and
 * the normal finish/core callback path. The runner itself is mocked here; its internals are covered
 * by {@code StepTaskRunnerTest}.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:uiexec-step-flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "testhub.uiexec.core-base-url=http://127.0.0.1:1",
        "testhub.uiexec.watchdog-enabled=false",
        "testhub.uiexec.artifact-dir=./target/test-artifacts-step"
})
@AutoConfigureMockMvc
class StepTaskFlowTest {

    private static final String AGENT_TOKEN = "testhub-dev-agent-token";
    private static final String SERVICE_TOKEN = "testhub-dev-service-token";
    private static final String CASE_ID = "case-step-flow";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CoreClient coreClient;

    @MockBean
    private AgentClient agentClient;

    @MockBean
    private StepTaskRunner stepTaskRunner;

    @Test
    void stepTasksQueuePerDeviceAndFinishThroughNormalPath() throws Exception {
        when(coreClient.fetchExecutionContext(CASE_ID)).thenReturn(new ExecutionContext(
                CASE_ID, "步骤流程用例", "APPROVED",
                new ExecutionContext.Bdd("已打开 App", "按步骤执行", "进入首页"), null, null));
        registerDevice();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(stepTaskRunner.run(any())).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return StepTaskRunner.StepRunResult.success("2/2 steps passed");
        });

        String first = createStepTask("run-step-1");
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        mockMvc.perform(get("/api/v1/ui-execution/tasks/" + first)
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.executorType").value("STEP"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());

        String second = createStepTask("run-step-2");
        mockMvc.perform(get("/api/v1/ui-execution/tasks/" + second)
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.executorType").value("STEP"))
                .andExpect(jsonPath("$.queuePosition").value(1));

        release.countDown();

        awaitStatus(first, "SUCCESS");
        JsonNode secondDetail = awaitStatus(second, "SUCCESS");
        assertThat(secondDetail.path("failureReason").isNull()).isTrue();

        // STEP tasks are driven by the orchestrator, never POSTed to /agent/tasks.
        verify(agentClient, never()).dispatch(anyString(), any());
        org.mockito.ArgumentCaptor<StepTaskRunner.Context> contextCaptor =
                org.mockito.ArgumentCaptor.forClass(StepTaskRunner.Context.class);
        verify(stepTaskRunner, times(2)).run(contextCaptor.capture());
        StepTaskRunner.Context firstContext = contextCaptor.getAllValues().get(0);
        assertThat(firstContext.agentBaseUrl()).isEqualTo("http://127.0.0.1:8090");
        assertThat(firstContext.deviceSerial()).isEqualTo("step-serial");
        assertThat(firstContext.caseTitle()).isEqualTo("步骤流程用例");
        assertThat(firstContext.steps()).hasSize(2);
        assertThat(firstContext.steps().get(0).stepTypeName()).isEqualTo("CLICK");
        assertThat(firstContext.steps().get(0).selector()).isEqualTo("登录");
        assertThat(firstContext.steps().get(1).stepTypeName()).isEqualTo("ASSERT_TEXT");
        verify(coreClient, timeout(5_000).times(2)).postPlanItemResult(any());
    }

    private void registerDevice() throws Exception {
        mockMvc.perform(post("/api/v1/ui-execution/agents/register")
                        .header("X-Agent-Token", AGENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"agent-step","host":"localhost","baseUrl":"http://127.0.0.1:8090",
                                 "devices":[{"serial":"step-serial","name":"Pixel","model":"sdk",
                                             "platform":"android","tags":["android"],"online":true}]}
                                """))
                .andExpect(status().isOk());
    }

    private String createStepTask(String planRunId) throws Exception {
        String body = """
                {"planId":"plan-step","planRunId":"%s","caseId":"%s","caseTitle":"步骤流程用例",
                 "executorType":"STEP","targetDeviceSelector":{"tags":["android"]},
                 "timeoutSec":60,"stepTimeoutSec":10,
                 "steps":[{"seq":1,"stepType":"CLICK","selectorType":"TEXT","selector":"登录"},
                          {"seq":2,"stepType":"ASSERT_TEXT","selectorType":"TEXT","selector":"首页"}]}
                """.formatted(planRunId, CASE_ID);
        MvcResult result = mockMvc.perform(post("/api/v1/ui-execution/tasks")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.executorType").value("STEP"))
                .andExpect(jsonPath("$.deviceSerial").value("step-serial"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("taskId").asText();
    }

    private JsonNode awaitStatus(String taskId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        JsonNode latest = null;
        while (System.currentTimeMillis() < deadline) {
            latest = objectMapper.readTree(mockMvc.perform(get("/api/v1/ui-execution/tasks/" + taskId)
                            .header("X-Service-Token", SERVICE_TOKEN))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray());
            if (expected.equals(latest.path("status").asText())) {
                return latest;
            }
            Thread.sleep(50L);
        }
        fail("任务未在预期时间内进入 " + expected + " 状态: " + latest);
        return latest;
    }
}
