package com.testhub.apiexec.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.apiexec.config.ApiExecProperties;
import com.testhub.apiexec.domain.ApiTaskEntity;
import com.testhub.apiexec.domain.ApiTaskStatus;
import com.testhub.common.security.SecurityProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Callback payload construction and delivery against a local HTTP stub. */
class CoreCallbackClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private ExecutorService serverExecutor;
    private CountDownLatch received;
    private final AtomicReference<Captured> captured = new AtomicReference<>();
    private volatile int responseStatus;

    @BeforeEach
    void startServer() throws IOException {
        received = new CountDownLatch(1);
        captured.set(null);
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/plan-runs/items/result", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            captured.set(new Captured(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-Service-Token"),
                    exchange.getRequestHeaders().getFirst("Content-Type"), body));
            received.countDown();
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
        });
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void postsContractPayloadWithServiceTokenAndAbsoluteReportUrl() throws Exception {
        CoreCallbackClient client = newClient(true);
        ApiTaskEntity task = completedTask(ApiTaskStatus.SUCCESS, null,
                "3/3 assertions passed, HTTP 200, 123ms");

        client.postPlanItemResult(PlanItemResultPayload.from(task, "http://127.0.0.1:8084/"));

        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        Captured request = captured.get();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/internal/v1/plan-runs/items/result");
        assertThat(request.serviceToken()).isEqualTo("stub-service-token");
        assertThat(request.contentType()).contains("application/json");

        JsonNode payload = objectMapper.readTree(request.body());
        assertThat(payload.path("planRunId").asText()).isEqualTo("run-1");
        assertThat(payload.path("caseId").asText()).isEqualTo("case-1");
        assertThat(payload.path("taskId").asText()).isEqualTo("api-task-1");
        assertThat(payload.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(payload.path("executorType").asText()).isEqualTo("API");
        assertThat(payload.path("failureReason").isNull()).isTrue();
        assertThat(payload.path("failureReportUrl").asText())
                .isEqualTo("http://127.0.0.1:8084/api/v1/api-execution/tasks/api-task-1/report");
        assertThat(payload.path("summary").asText()).contains("3/3 assertions passed");
    }

    @Test
    void payloadCarriesFailureReasonForFailedTask() {
        ApiTaskEntity task = completedTask(ApiTaskStatus.FAILED, "ASSERTION_FAILED",
                "1/1 assertions failed, HTTP 200, 12ms");
        PlanItemResultPayload payload = PlanItemResultPayload.from(task, "http://apiexec:8084");

        assertThat(payload.status()).isEqualTo("FAILED");
        assertThat(payload.failureReason()).isEqualTo("ASSERTION_FAILED");
        assertThat(payload.executorType()).isEqualTo("API");
        assertThat(payload.failureReportUrl())
                .isEqualTo("http://apiexec:8084/api/v1/api-execution/tasks/api-task-1/report");
    }

    @Test
    void callbackFailureIsOnlyLogged() throws Exception {
        responseStatus = 500;
        CoreCallbackClient client = newClient(true);
        ApiTaskEntity task = completedTask(ApiTaskStatus.TIMEOUT, "TIMEOUT", "request timed out after 1s");

        assertThatCode(() -> client.postPlanItemResult(
                PlanItemResultPayload.from(task, "http://127.0.0.1:8084"))).doesNotThrowAnyException();
        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void disabledCallbackSendsNothing() throws Exception {
        CoreCallbackClient client = newClient(false);
        ApiTaskEntity task = completedTask(ApiTaskStatus.SUCCESS, null, "ok");

        client.postPlanItemResult(PlanItemResultPayload.from(task, "http://127.0.0.1:8084"));

        assertThat(received.await(300, TimeUnit.MILLISECONDS)).isFalse();
    }

    private CoreCallbackClient newClient(boolean enabled) {
        ApiExecProperties properties = new ApiExecProperties();
        properties.setCoreBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.setPublicBaseUrl("http://127.0.0.1:8084");
        properties.setServiceToken("stub-service-token");
        properties.setCallbackEnabled(enabled);
        SecurityProperties security = new SecurityProperties();
        return new CoreCallbackClient(RestClient.builder(), properties, security);
    }

    private ApiTaskEntity completedTask(ApiTaskStatus status, String failureReason, String summary) {
        ApiTaskEntity task = new ApiTaskEntity("api-task-1", "case-1", "POST",
                "http://127.0.0.1:9099/login", status, 30, Instant.now());
        task.setPlanId("plan-1");
        task.setPlanRunId("run-1");
        task.setCaseName("登录接口");
        task.setFailureReason(failureReason);
        task.setSummary(summary);
        return task;
    }

    private record Captured(String method, String path, String serviceToken, String contentType, String body) {
    }
}
