package com.testhub.apiexec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.testhub.apiexec.client.CoreCallbackClient;
import com.testhub.apiexec.config.ApiExecProperties;
import com.testhub.apiexec.queue.TaskQueue;
import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.JwtTokenProvider;
import com.testhub.common.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end execution tests against a hermetic local {@link HttpServer}:
 * success, assertion failure, STATUS_CODE-expected failure, timeouts and
 * cancellation. No external network or database is involved.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apiexec-flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "testhub.apiexec.core-base-url=http://127.0.0.1:1",
        "testhub.apiexec.callback-enabled=false",
        "testhub.apiexec.workers=1",
        "testhub.apiexec.instance-id=test-instance",
        "testhub.apiexec.public-base-url=http://127.0.0.1:8084"
})
@AutoConfigureMockMvc
class ApiExecutionFlowTest {

    private static final String SERVICE_TOKEN = "testhub-dev-service-token";
    private static final Set<String> TERMINAL = Set.of("SUCCESS", "FAILED", "TIMEOUT", "CANCELLED");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ApiExecProperties properties;

    @Autowired
    private TaskQueue taskQueue;

    @MockBean
    private CoreCallbackClient callbackClient;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private CountDownLatch blockReceived;
    private CountDownLatch blockRelease;
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        reset(callbackClient);
        blockReceived = new CountDownLatch(1);
        blockRelease = new CountDownLatch(1);
        captured.set(null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            capture(exchange);
            respond(exchange, 200, "{\"code\":0,\"message\":\"success\"}", Map.of("X-Trace", "trace-abc"));
        });
        server.createContext("/fail", exchange -> {
            capture(exchange);
            respond(exchange, 500, "{\"code\":1,\"message\":\"boom\"}", Map.of());
        });
        server.createContext("/slow", exchange -> {
            capture(exchange);
            sleep(2_000);
            respond(exchange, 200, "{\"code\":0}", Map.of());
        });
        server.createContext("/block", exchange -> {
            capture(exchange);
            blockReceived.countDown();
            try {
                blockRelease.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{\"code\":0}", Map.of());
        });
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        blockRelease.countDown();
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void executesRealRequestAndPassesAllAssertionTypes() throws Exception {
        String taskId = createTask("/ok", "POST", 5, assertions(
                assertion("STATUS_CODE", null, "EQ", "200"),
                assertion("JSON_PATH", "$.code", "EQ", "0"),
                assertion("BODY_CONTAINS", null, "CONTAINS", "success"),
                assertion("RESPONSE_TIME_MS", null, "LT", "5000"),
                assertion("HEADER_CONTAINS", "X-Trace", "CONTAINS", "trace-abc")));

        JsonNode detail = awaitTerminal(taskId);

        assertThat(detail.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(detail.path("failureReason").isNull()).isTrue();
        assertThat(detail.path("httpStatus").asInt()).isEqualTo(200);
        assertThat(detail.path("durationMs").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(detail.path("workerId").asText()).isEqualTo(properties.getInstanceId() + "-1");
        assertThat(detail.path("summary").asText()).contains("5/5 assertions passed").contains("HTTP 200");
        assertThat(detail.path("assertionResults")).hasSize(5);
        detail.path("assertionResults").forEach(result -> assertThat(result.path("passed").asBoolean()).isTrue());
        assertThat(detail.path("response").path("status").asInt()).isEqualTo(200);
        assertThat(detail.path("response").path("body").asText()).contains("\"success\"");
        assertThat(detail.path("request").path("url").asText())
                .contains("/ok").contains("lang=zh").contains("q=a%20b");
        assertThat(detail.path("failureReportUrl").asText())
                .isEqualTo("http://127.0.0.1:8084/api/v1/api-execution/tasks/" + taskId + "/report");

        CapturedRequest request = captured.get();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/ok");
        assertThat(request.query()).contains("lang=zh").contains("q=a%20b");
        assertThat(request.body()).isEqualTo("{\"username\":\"tester\"}");
        assertThat(request.customHeader()).isEqualTo("abc");
        verify(callbackClient, timeout(2000)).postPlanItemResult(argThat(payload ->
                taskId.equals(payload.taskId()) && "SUCCESS".equals(payload.status())));
    }

    @Test
    void failingAssertionsProduceFailedReport() throws Exception {
        String taskId = createTask("/fail", "GET", 5, assertions(
                assertion("STATUS_CODE", null, "EQ", "200"),
                assertion("JSON_PATH", "$.code", "EQ", "0")));

        JsonNode detail = awaitTerminal(taskId);
        assertThat(detail.path("status").asText()).isEqualTo("FAILED");
        assertThat(detail.path("failureReason").asText()).isEqualTo("ASSERTION_FAILED");
        assertThat(detail.path("httpStatus").asInt()).isEqualTo(500);
        assertThat(detail.path("summary").asText()).contains("2/2 assertions failed");
        assertThat(detail.path("assertionResults")).hasSize(2);
        detail.path("assertionResults").forEach(result -> assertThat(result.path("passed").asBoolean()).isFalse());

        MvcResult reportResult = mockMvc.perform(get("/api/v1/api-execution/tasks/" + taskId + "/report")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode report = objectMapper.readTree(reportResult.getResponse().getContentAsByteArray());
        assertThat(report.path("taskId").asText()).isEqualTo(taskId);
        assertThat(report.path("status").asText()).isEqualTo("FAILED");
        assertThat(report.path("failureReason").asText()).isEqualTo("ASSERTION_FAILED");
        assertThat(report.path("workerId").asText()).isEqualTo("test-instance-1");
        assertThat(report.path("request").path("method").asText()).isEqualTo("GET");
        assertThat(report.path("request").path("url").asText()).contains("/fail");
        assertThat(report.path("response").path("status").asInt()).isEqualTo(500);
        assertThat(report.path("response").path("body").asText()).contains("boom");
        assertThat(report.path("assertions")).hasSize(2);
        assertThat(report.path("summary").asText()).contains("2/2 assertions failed");
    }

    @Test
    void non2xxWithoutStatusAssertionFailsWithHttpError() throws Exception {
        String taskId = createTask("/fail", "GET", 5, assertions(
                assertion("BODY_CONTAINS", null, "CONTAINS", "\"code\":1")));

        JsonNode detail = awaitTerminal(taskId);
        assertThat(detail.path("status").asText()).isEqualTo("FAILED");
        assertThat(detail.path("failureReason").asText()).isEqualTo("HTTP_ERROR");
        assertThat(detail.path("httpStatus").asInt()).isEqualTo(500);
    }

    @Test
    void non2xxWithoutAnyAssertionFailsWithHttpError() throws Exception {
        String taskId = createTask("/fail", "GET", 5, List.of());

        JsonNode detail = awaitTerminal(taskId);
        assertThat(detail.path("status").asText()).isEqualTo("FAILED");
        assertThat(detail.path("failureReason").asText()).isEqualTo("HTTP_ERROR");
    }

    @Test
    void statusCodeAssertionCanExpectAndPassFor500() throws Exception {
        String taskId = createTask("/fail", "GET", 5, assertions(
                assertion("STATUS_CODE", null, "EQ", "500"),
                assertion("JSON_PATH", "$.code", "EQ", "1")));

        JsonNode detail = awaitTerminal(taskId);
        assertThat(detail.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(detail.path("httpStatus").asInt()).isEqualTo(500);
        assertThat(detail.path("summary").asText()).contains("2/2 assertions passed");
    }

    @Test
    void requestTimeoutProducesTimeoutStatus() throws Exception {
        String taskId = createTask("/slow", "GET", 1, assertions(
                assertion("STATUS_CODE", null, "EQ", "200")));

        JsonNode detail = awaitTerminal(taskId);
        assertThat(detail.path("status").asText()).isEqualTo("TIMEOUT");
        assertThat(detail.path("failureReason").asText()).isEqualTo("TIMEOUT");
        assertThat(detail.path("summary").asText()).contains("timed out after 1s");
        assertThat(detail.path("httpStatus").isMissingNode() || detail.path("httpStatus").isNull()).isTrue();
    }

    @Test
    void cancelQueuedTaskMarksItCancelled() throws Exception {
        String blocking = createTask("/block", "GET", 10, List.of());
        assertThat(blockReceived.await(5, TimeUnit.SECONDS)).isTrue();
        awaitStatus(blocking, "RUNNING");

        String queued = createTask("/ok", "GET", 5, List.of());
        assertThat(taskQueue.size()).isEqualTo(1);
        JsonNode queuedDetail = detail(queued);
        assertThat(queuedDetail.path("status").asText()).isEqualTo("PENDING");
        assertThat(queuedDetail.path("workerId").isNull()).isTrue();

        mockMvc.perform(post("/api/v1/api-execution/tasks/" + queued + "/cancel")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(queued))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(taskQueue.size()).isZero();
        JsonNode cancelled = detail(queued);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("failureReason").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("summary").asText()).contains("cancelled before execution");
        assertThat(cancelled.path("workerId").isNull()).isTrue();
        assertThat(cancelled.path("httpStatus").isNull()).isTrue();
        assertThat(cancelled.path("assertionResults")).isEmpty();

        blockRelease.countDown();
        assertThat(awaitTerminal(blocking).path("status").asText()).isEqualTo("SUCCESS");
        verify(callbackClient, timeout(2000)).postPlanItemResult(argThat(payload ->
                queued.equals(payload.taskId()) && "CANCELLED".equals(payload.status())));
    }

    @Test
    void cancelRunningTaskAbortsAndMarksItCancelled() throws Exception {
        String taskId = createTask("/block", "GET", 10, List.of());
        assertThat(blockReceived.await(5, TimeUnit.SECONDS)).isTrue();
        JsonNode running = awaitStatus(taskId, "RUNNING");
        assertThat(running.path("workerId").asText()).isEqualTo("test-instance-1");

        mockMvc.perform(post("/api/v1/api-execution/tasks/" + taskId + "/cancel")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        JsonNode detail = detail(taskId);
        assertThat(detail.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(detail.path("summary").asText()).contains("cancelled while running");
        assertThat(detail.path("workerId").asText()).isEqualTo("test-instance-1");
        assertThat(taskQueue.size()).isZero();

        blockRelease.countDown();
        Thread.sleep(300);
        // The cancel path sends the terminal callback; the worker must not duplicate it.
        verify(callbackClient, timeout(2000)).postPlanItemResult(argThat(payload ->
                taskId.equals(payload.taskId()) && "CANCELLED".equals(payload.status())));
    }

    @Test
    void taskCreationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/api-execution/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/api-execution/tasks/does-not-exist/report"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/api-execution/stats")
                        .header("X-Agent-Token", "testhub-dev-agent-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void opsJwtCannotCreateButCanReadAndCancel() throws Exception {
        String ops = jwt(Role.OPS);
        mockMvc.perform(post("/api/v1/api-execution/tasks")
                        .header("Authorization", "Bearer " + ops)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/api-execution/stats")
                        .header("Authorization", "Bearer " + ops))
                .andExpect(status().isOk());
    }

    @Test
    void testerJwtCanCreateAndReadTask() throws Exception {
        String tester = jwt(Role.TESTER);
        MvcResult created = mockMvc.perform(post("/api/v1/api-execution/tasks")
                        .header("Authorization", "Bearer " + tester)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                taskPayload("/ok", "GET", 5, List.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String taskId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .path("taskId").asText();

        JsonNode detail = awaitTerminalWithToken(taskId, tester);
        assertThat(detail.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(detail.path("createdBy").asText()).isEqualTo("tester");
    }

    @Test
    void listsTasksByPlanRunAndStatus() throws Exception {
        String taskId = createTask("/ok", "GET", 5, List.of());
        JsonNode detail = awaitTerminal(taskId);
        String planRunId = detail.path("planRunId").asText();
        assertThat(planRunId).isNotBlank();

        mockMvc.perform(get("/api/v1/api-execution/tasks")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .param("planRunId", planRunId)
                        .param("status", "SUCCESS")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].taskId").value(taskId))
                .andExpect(jsonPath("$.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].workerId").value("test-instance-1"))
                .andExpect(jsonPath("$.items[0].summary").isNotEmpty());

        mockMvc.perform(get("/api/v1/api-execution/tasks")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .param("planRunId", planRunId)
                        .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/v1/api-execution/tasks")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsExposeWorkerCountAndStatusCounters() throws Exception {
        mockMvc.perform(get("/api/v1/api-execution/stats")
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workers").value(1))
                .andExpect(jsonPath("$.pending").isNumber())
                .andExpect(jsonPath("$.success").isNumber());
    }

    // ---------------------------------------------------------------- helpers

    private String createTask(String path, String method, int timeoutSec,
                              List<Map<String, Object>> assertions) throws Exception {
        Map<String, Object> payload = taskPayload(path, method, timeoutSec, assertions);
        MvcResult result = mockMvc.perform(post("/api/v1/api-execution/tasks")
                        .header("X-Service-Token", SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("taskId").asText();
    }

    private Map<String, Object> taskPayload(String path, String method, int timeoutSec,
                                            List<Map<String, Object>> assertions) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom", "abc");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("lang", "zh");
        query.put("q", "a b");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", method);
        request.put("baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        request.put("path", path);
        request.put("headers", headers);
        request.put("query", query);
        request.put("body", "{\"username\":\"tester\"}");
        request.put("timeoutSec", timeoutSec);

        Map<String, Object> payload = new LinkedHashMap<>();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        payload.put("planId", "plan-it");
        payload.put("planRunId", "run-" + suffix);
        payload.put("caseId", "case-" + suffix);
        payload.put("caseName", "集成测试用例");
        payload.put("request", request);
        payload.put("assertions", assertions);
        payload.put("createdBy", "tester");
        return payload;
    }

    private String jwt(Role role) {
        return jwtTokenProvider.createToken(new AuthPrincipal("user-" + role.name().toLowerCase(),
                role.name().toLowerCase(), role.name(), role));
    }

    private List<Map<String, Object>> assertions(Map<String, Object>... items) {
        return new ArrayList<>(List.of(items));
    }

    private Map<String, Object> assertion(String type, String path, String operator, String expected) {
        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("type", type);
        if (path != null) {
            assertion.put("path", path);
        }
        assertion.put("operator", operator);
        assertion.put("expected", expected);
        return assertion;
    }

    private JsonNode detail(String taskId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/api-execution/tasks/" + taskId)
                        .header("X-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    @SuppressWarnings("unchecked")
    private JsonNode awaitStatus(String taskId, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            last = detail(taskId);
            if (expected.equals(last.path("status").asText())) {
                return last;
            }
            Thread.sleep(50);
        }
        fail("task " + taskId + " did not reach " + expected + ", last=" + last);
        return last;
    }

    private JsonNode awaitTerminal(String taskId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            last = detail(taskId);
            if (TERMINAL.contains(last.path("status").asText())) {
                return last;
            }
            Thread.sleep(50);
        }
        fail("task " + taskId + " did not finish, last=" + last);
        return last;
    }

    private JsonNode awaitTerminalWithToken(String taskId, String token) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/api-execution/tasks/" + taskId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            last = objectMapper.readTree(result.getResponse().getContentAsByteArray());
            if (TERMINAL.contains(last.path("status").asText())) {
                return last;
            }
            Thread.sleep(50);
        }
        fail("task " + taskId + " did not finish, last=" + last);
        return last;
    }

    private void capture(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        captured.set(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("X-Custom"), body));
    }

    private void respond(HttpExchange exchange, int status, String body, Map<String, String> headers) {
        try {
            headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (IOException e) {
            // The client may have timed out / cancelled already; nothing to do.
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record CapturedRequest(String method, String path, String query, String customHeader, String body) {
    }
}
