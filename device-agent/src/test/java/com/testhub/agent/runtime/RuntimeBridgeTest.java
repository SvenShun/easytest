package com.testhub.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.testhub.agent.config.AgentProperties;
import com.testhub.agent.dispatch.AgentTask;
import com.testhub.agent.dispatch.CallbackClient;
import com.testhub.agent.dispatch.TaskDispatchRequest;
import com.testhub.agent.dispatch.TaskReporter;
import com.testhub.agent.http.HttpJsonClient;
import com.testhub.agent.skills.SkillsCache;

/** Runtime bridge contract tests against a loopback fake ai-runtime. */
class RuntimeBridgeTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> runtimeRequest = new AtomicReference<>();
    private final AtomicReference<String> resultBody = new AtomicReference<>();
    private final AtomicInteger runtimeCalls = new AtomicInteger();
    private final List<String> logBodies = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private String baseUrl;
    private AgentProperties properties;
    private HttpJsonClient http;
    private CallbackClient callbacks;
    private TaskReporter reporter;

    private volatile String runtimeStatus = "SUCCESS";
    private volatile String runtimeReason = null;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/runtime/tasks", exchange -> {
            runtimeCalls.incrementAndGet();
            runtimeRequest.set(readBody(exchange));
            String reason = runtimeReason == null ? "null" : "\"" + runtimeReason + "\"";
            String body = "{\"taskId\":\"task-ai\",\"status\":\"" + runtimeStatus
                    + "\",\"reasonCategory\":" + reason
                    + ",\"summary\":\"runtime summary\",\"totalSteps\":3,\"elapsedMs\":1200}";
            respond(exchange, 200, body);
        });
        server.createContext("/runtime/health", exchange -> respond(exchange, 200,
                "{\"status\":\"UP\"}"));
        server.createContext("/result", exchange -> {
            resultBody.set(readBody(exchange));
            respond(exchange, 200, "{}");
        });
        server.createContext("/logs", exchange -> {
            logBodies.add(readBody(exchange));
            respond(exchange, 204, null);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        properties = new AgentProperties();
        properties.setDataDir(tempDir.resolve("agent").toString());
        properties.setToken("agent-token");
        properties.setRuntimeUrl(baseUrl);
        properties.setBaseUrl("http://127.0.0.1:8090");
        http = new HttpJsonClient(mapper);
        callbacks = new CallbackClient(http);
        reporter = new TaskReporter(callbacks, properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsContractPayloadAndReportsSuccess() throws Exception {
        RuntimeBridge bridge = bridge((dir, args, timeout) -> 1);
        bridge.execute(new AgentTask("task-ai", "AI_RUNTIME"), request(null, baseUrl + "/result"));

        JsonNode sent = mapper.readTree(runtimeRequest.get());
        assertThat(sent.path("taskId").asText()).isEqualTo("task-ai");
        assertThat(sent.path("bdd").path("given").asText()).isEqualTo("已打开 App");
        assertThat(sent.path("bdd").path("when").asText()).isEqualTo("输入账号密码");
        assertThat(sent.path("bdd").path("then").asText()).isEqualTo("进入首页");
        assertThat(sent.path("skills").path("repo").isNull()).isTrue();
        assertThat(sent.path("skills").path("commit").isNull()).isTrue();
        assertThat(sent.path("skills").path("dir").isNull()).isTrue();
        assertThat(sent.path("maxSteps").asInt()).isEqualTo(25);
        assertThat(sent.path("timeouts").path("totalSec").asInt()).isEqualTo(45);
        assertThat(sent.path("timeouts").path("stepSec").asInt()).isEqualTo(12);
        assertThat(sent.path("model").path("provider").asText()).isEqualTo("mock");
        assertThat(sent.path("model").path("modelId").asText()).isEqualTo("mock-vision");
        assertThat(sent.path("device").path("baseUrl").asText()).isEqualTo("http://127.0.0.1:8090");
        assertThat(sent.path("device").path("serial").asText()).isEqualTo("emulator-5554");
        assertThat(sent.path("callbacks").path("token").asText()).isEqualTo("runtime-token");
        assertThat(sent.path("callbacks").path("stepUrl").asText()).isEqualTo(baseUrl + "/steps");
        assertThat(sent.path("callbacks").path("logUrl").asText()).isEqualTo(baseUrl + "/logs");

        JsonNode result = mapper.readTree(resultBody.get());
        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("summary").asText()).isEqualTo("runtime summary");
        assertThat(result.path("artifacts").isArray()).isTrue();
        assertThat(bridge.isReachable()).isTrue();
    }

    @Test
    void mapsRuntimeFailureAndReason() throws Exception {
        runtimeStatus = "FAILED";
        runtimeReason = "ELEMENT_NOT_FOUND";
        RuntimeBridge bridge = bridge((dir, args, timeout) -> 1);
        bridge.execute(new AgentTask("task-ai", "AI_RUNTIME"), request(null, baseUrl + "/result"));

        JsonNode result = mapper.readTree(resultBody.get());
        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("reasonCategory").asText()).isEqualTo("ELEMENT_NOT_FOUND");
        assertThat(result.path("failureReason").asText()).isEqualTo("ELEMENT_NOT_FOUND");
    }

    @Test
    void runtimeUnavailableReportsExecutorUnavailable() throws Exception {
        properties.setRuntimeUrl("http://127.0.0.1:1");
        RuntimeBridge bridge = bridge((dir, args, timeout) -> 1);
        bridge.execute(new AgentTask("task-ai", "AI_RUNTIME"), request(null, baseUrl + "/result"));

        JsonNode result = mapper.readTree(resultBody.get());
        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("reasonCategory").asText()).isEqualTo("EXECUTOR_UNAVAILABLE");
        assertThat(result.path("summary").asText()).contains("runtime unavailable");
    }

    @Test
    void skillsFailureFailsFastWithoutCallingRuntime() throws Exception {
        RuntimeBridge bridge = bridge((dir, args, timeout) -> 1);
        TaskDispatchRequest request = request(
                new TaskDispatchRequest.Skills("file:///tmp/missing-skills", "abc123"),
                baseUrl + "/result");
        bridge.execute(new AgentTask("task-ai", "AI_RUNTIME"), request);

        JsonNode result = mapper.readTree(resultBody.get());
        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("reasonCategory").asText()).isEqualTo("SKILLS_UNAVAILABLE");
        assertThat(runtimeCalls.get()).isZero();
    }

    private RuntimeBridge bridge(com.testhub.agent.skills.GitCommandRunner runner) {
        SkillsCache skillsCache = new SkillsCache(properties, runner);
        return new RuntimeBridge(properties, http, skillsCache, reporter);
    }

    private TaskDispatchRequest request(TaskDispatchRequest.Skills skills, String resultUrl) {
        TaskDispatchRequest.Model model = new TaskDispatchRequest.Model("mock", "", "",
                "mock-vision");
        return new TaskDispatchRequest("task-ai", "AI_RUNTIME", "case-ai", "AI case",
                new TaskDispatchRequest.Bdd("已打开 App", "输入账号密码", "进入首页"),
                null,
                new TaskDispatchRequest.Device("emulator-5554", "android", List.of("android")),
                skills,
                new TaskDispatchRequest.Timeouts(45, 12),
                model,
                new TaskDispatchRequest.Callbacks(baseUrl + "/steps", baseUrl + "/logs",
                        resultUrl, "runtime-token"));
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }
}
