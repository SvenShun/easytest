package com.testhub.core.feature.metersphere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.testhub.core.feature.plan.PlanCase;
import com.testhub.core.feature.plan.PlanRun;
import com.testhub.core.feature.plan.PlanRunItem;
import com.testhub.core.feature.plan.RunItemStatus;
import com.testhub.core.feature.plan.RunStatus;
import com.testhub.core.feature.plan.TestPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test for the MeterSphere result write-back
 * (POST /test-plan/functional/case/run).
 */
class MeterSphereResultSinkTest {

    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastToken = new AtomicReference<>();
    private final AtomicReference<String> lastCsrf = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login", exchange -> respond(exchange, 200,
                "{\"code\":100200,\"message\":null,\"data\":{\"sessionId\":\"session-from-login\","
                        + "\"csrfToken\":\"csrf-from-login\"}}"));
        server.createContext("/test-plan/functional/case/run", exchange -> {
            calls.incrementAndGet();
            lastToken.set(exchange.getRequestHeaders().getFirst("X-AUTH-TOKEN"));
            lastCsrf.set(exchange.getRequestHeaders().getFirst("CSRF-TOKEN"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"code\":100200,\"message\":null,\"data\":null}");
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void writesSuccessResultWithMappingAndHeaders() throws Exception {
        MeterSphereResultSink sink = sink(properties(true));

        sink.write(plan(), planCase(), run(), item(RunItemStatus.SUCCESS));

        assertThat(calls.get()).isEqualTo(1);
        assertThat(lastToken.get()).isEqualTo("session-token");
        assertThat(lastCsrf.get()).isEqualTo("csrf-token");
        JsonNode body = mapper.readTree(lastBody.get());
        assertThat(body.get("projectId").asText()).isEqualTo("project-1");
        assertThat(body.get("id").asText()).isEqualTo("plan-case-1");
        assertThat(body.get("testPlanId").asText()).isEqualTo("ms-plan-1");
        assertThat(body.get("caseId").asText()).isEqualTo("ms-case-1");
        assertThat(body.get("lastExecResult").asText()).isEqualTo("SUCCESS");
        assertThat(body.get("content").asText())
                .contains("run-1")
                .contains("task-1")
                .contains("报告");
    }

    @Test
    void mapsFailureToErrorAndTimeoutToBlocked() throws Exception {
        MeterSphereResultSink sink = sink(properties(true));

        sink.write(plan(), planCase(), run(), item(RunItemStatus.FAILED));
        assertThat(mapper.readTree(lastBody.get()).get("lastExecResult").asText()).isEqualTo("ERROR");

        sink.write(plan(), planCase(), run(), item(RunItemStatus.TIMEOUT));
        assertThat(mapper.readTree(lastBody.get()).get("lastExecResult").asText()).isEqualTo("BLOCKED");
    }

    @Test
    void logsInWhenCredentialsAreConfigured() throws Exception {
        MeterSphereProperties properties = properties(true);
        properties.setToken("");
        properties.setCsrfToken("");
        properties.setUsername("admin");
        properties.setPassword("metersphere");
        MeterSphereResultSink sink = sink(properties);

        sink.write(plan(), planCase(), run(), item(RunItemStatus.SUCCESS));

        assertThat(lastToken.get()).isEqualTo("session-from-login");
        assertThat(lastCsrf.get()).isEqualTo("csrf-from-login");
        assertThat(mapper.readTree(lastBody.get()).get("lastExecResult").asText()).isEqualTo("SUCCESS");
    }

    @Test
    void doesNothingWhenDisabledOrUnmapped() {
        MeterSphereResultSink disabled = sink(properties(false));
        disabled.write(plan(), planCase(), run(), item(RunItemStatus.SUCCESS));

        MeterSphereResultSink unmapped = sink(properties(true));
        unmapped.write(plan(), new PlanCase("pc", "plan-1", "case-1", null, Instant.now()),
                run(), item(RunItemStatus.SUCCESS));

        assertThat(calls.get()).isZero();
    }

    private MeterSphereResultSink sink(MeterSphereProperties properties) {
        return new MeterSphereResultSink(properties, new MeterSphereSession(properties, mapper));
    }

    private MeterSphereProperties properties(boolean enabled) {
        MeterSphereProperties properties = new MeterSphereProperties();
        properties.setEnabled(enabled);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setProjectId("project-1");
        properties.setToken("session-token");
        properties.setCsrfToken("csrf-token");
        return properties;
    }

    private TestPlan plan() {
        TestPlan plan = new TestPlan("plan-1", "回归计划", "", "ACTIVE", "tester",
                Instant.now(), Instant.now());
        plan.setMetersphereTestPlanId("ms-plan-1");
        return plan;
    }

    private PlanCase planCase() {
        PlanCase planCase = new PlanCase("pc", "plan-1", "case-1", null, Instant.now());
        planCase.setMetersphereCaseId("ms-case-1");
        planCase.setMetersphereTestPlanCaseId("plan-case-1");
        return planCase;
    }

    private PlanRun run() {
        return new PlanRun("run-1", "plan-1", RunStatus.RUNNING, "tester", Instant.now());
    }

    private PlanRunItem item(RunItemStatus status) {
        PlanRunItem item = new PlanRunItem("item-1", "run-1", "case-1", "登录用例",
                null, status, Instant.now());
        item.setTaskId("task-1");
        item.setFailureReportUrl("http://reports/task-1.json");
        return item;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
