package com.testhub.agent.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.agent.DeviceAgentApplication;

@SpringBootTest(classes = DeviceAgentApplication.class, properties = {
        "testhub.agent.registration-enabled=false",
        "testhub.agent.data-dir=target/test-data/agent",
        "testhub.agent.ui-execution-url=http://127.0.0.1:1",
        "testhub.agent.runtime-url=http://127.0.0.1:1",
        "testhub.agent.heartbeat-interval-sec=3600"
})
@AutoConfigureMockMvc
class DeviceAgentApplicationTest {

    private static final String TOKEN = "testhub-dev-agent-token";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoadsAndHealthIsPublic() throws Exception {
        mvc.perform(get("/agent/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.mode").value("simulated"))
                .andExpect(jsonPath("$.devices.length()").value(2))
                .andExpect(jsonPath("$.runtimeReachable").value(false))
                .andExpect(jsonPath("$.uiExecutionReachable").value(false));
    }

    @Test
    void agentEndpointsRequireToken() throws Exception {
        mvc.perform(get("/agent/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(post("/agent/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"t-1\",\"executorType\":\"SCRIPT\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void devicesAreListedWithToken() throws Exception {
        mvc.perform(get("/agent/devices").header("X-Agent-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.devices[0].serial").value("emulator-5554"))
                .andExpect(jsonPath("$.devices[0].tags[1]").value("simulated"))
                .andExpect(jsonPath("$.devices[1].serial").value("simulator-0001"));
    }

    @Test
    void deviceDriverEndpointsWorkFromLoopback() throws Exception {
        mvc.perform(get("/device/emulator-5554/ui-tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(720))
                .andExpect(jsonPath("$.height").value(1280))
                .andExpect(jsonPath("$.screen.app").value("com.testhub.demo"))
                .andExpect(jsonPath("$.screen.title").value("TestHub Demo"));

        mvc.perform(post("/device/emulator-5554/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"click\",\"target\":{\"text\":\"登录\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.screen").value("login"));

        mvc.perform(post("/device/emulator-5554/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.screen").value("home"));

        // ai-runtime sends the action type as "type" (compatibility alias).
        mvc.perform(post("/device/simulator-0001/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"click\",\"target\":{\"text\":\"登录\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.screen").value("login"));

        mvc.perform(post("/device/simulator-0001/reset"))
                .andExpect(status().isOk());
    }

    @Test
    void screenshotIsPng() throws Exception {
        MvcResult result = mvc.perform(get("/device/emulator-5554/screenshot"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();
        byte[] png = result.getResponse().getContentAsByteArray();
        assertThat(png.length).isGreaterThan(1000);
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
    }

    @Test
    void unknownDeviceReturns404() throws Exception {
        mvc.perform(get("/device/does-not-exist/ui-tree"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("device not found"));
    }

    @Test
    void unknownExecutorIsRejected() throws Exception {
        mvc.perform(post("/agent/tasks")
                        .header("X-Agent-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"t-unknown\",\"executorType\":\"API\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_EXECUTOR"));
    }

    @Test
    void acceptsScriptTaskAndRunsItAsynchronously() throws Exception {
        String taskId = "task-" + UUID.randomUUID();
        String payload = """
                {"taskId":"%s","executorType":"SCRIPT","caseId":"c1","caseTitle":"demo",
                 "script":{"id":"s1","scriptType":"TESTNG","name":"demo.zip","entryCommand":"sh run.sh"},
                 "device":{"serial":"emulator-5554","platform":"android","tags":["android"]},
                 "timeouts":{"totalSec":10,"stepSec":5},
                 "callbacks":{"stepUrl":"http://127.0.0.1:1/steps",
                              "logUrl":"http://127.0.0.1:1/logs",
                              "resultUrl":"http://127.0.0.1:1/result","token":"%s"}}
                """.formatted(taskId, TOKEN);

        mvc.perform(post("/agent/tasks")
                        .header("X-Agent-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));

        // Duplicate dispatch of a running/finished task is a conflict.
        mvc.perform(post("/agent/tasks")
                        .header("X-Agent-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_RUNNING"));

        JsonNode task = awaitTerminal(taskId);
        assertThat(task.path("executorType").asText()).isEqualTo("SCRIPT");
        assertThat(task.path("status").asText()).isIn("FAILED", "TIMEOUT", "CANCELLED");
    }

    private JsonNode awaitTerminal(String taskId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000L;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mvc.perform(get("/agent/tasks/" + taskId)
                            .header("X-Agent-Token", TOKEN))
                    .andExpect(status().isOk())
                    .andReturn();
            last = objectMapper.readTree(result.getResponse().getContentAsString());
            String state = last.path("status").asText();
            if (state.equals("FAILED") || state.equals("SUCCESS")
                    || state.equals("TIMEOUT") || state.equals("CANCELLED")) {
                return last;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("task did not reach a terminal state: " + last);
    }
}
