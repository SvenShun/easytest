package com.testhub.agent.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

/**
 * End-to-end script execution against a loopback bundle/callback server. No
 * external network, no Docker, no device needed.
 */
class ScriptExecutorIntegrationTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> stepBodies = new CopyOnWriteArrayList<>();
    private final List<String> logBodies = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> resultBody = new AtomicReference<>();

    private HttpServer server;
    private String baseUrl;
    private ScriptExecutor executor;
    private AgentProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bundle-ok", exchange -> serveBundle(exchange, okBundle()));
        server.createContext("/bundle-fail", exchange -> serveBundle(exchange, failingBundle()));
        server.createContext("/steps", exchange -> {
            stepBodies.add(readBody(exchange));
            respond(exchange, 201, "{}");
        });
        server.createContext("/logs", exchange -> {
            logBodies.add(readBody(exchange));
            respond(exchange, 204, null);
        });
        server.createContext("/result", exchange -> {
            resultBody.set(readBody(exchange));
            respond(exchange, 200, "{\"taskId\":\"t\"}");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        properties = new AgentProperties();
        properties.setDataDir(tempDir.resolve("agent-data").toString());
        properties.setToken("test-token");

        HttpJsonClient http = new HttpJsonClient(mapper);
        CallbackClient callbacks = new CallbackClient(http);
        TaskReporter reporter = new TaskReporter(callbacks, properties);
        executor = new ScriptExecutor(properties, http, callbacks, new JunitXmlParser(),
                new ArtifactCollector(), reporter);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void executesBundleParsesJunitAndReportsArtifacts() throws Exception {
        TaskDispatchRequest request = request("task-ok", baseUrl + "/bundle-ok");
        executor.execute(new AgentTask("task-ok", "SCRIPT"), request);

        JsonNode result = mapper.readTree(resultBody.get());
        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("reasonCategory").isNull()).isTrue();

        List<String> junitArtifacts = new ArrayList<>();
        for (JsonNode artifact : result.path("artifacts")) {
            if ("JUNIT".equals(artifact.path("type").asText())) {
                junitArtifacts.add(new String(Base64.getDecoder().decode(
                        artifact.path("contentBase64").asText()), StandardCharsets.UTF_8));
            }
            assertThat(artifact.path("contentBase64").asText()).isNotBlank();
        }
        assertThat(junitArtifacts).hasSize(1);
        assertThat(junitArtifacts.get(0)).contains("com.testhub.LoginTest");
        assertThat(result.path("artifacts").toString()).contains("\"type\":\"LOG\"");

        assertThat(stepBodies).hasSize(1);
        JsonNode step = mapper.readTree(stepBodies.get(0));
        assertThat(step.path("seq").asInt()).isEqualTo(1);
        assertThat(step.path("status").asText()).isEqualTo("OK");
        assertThat(step.path("decision").asText()).isEqualTo("com.testhub.LoginTest.testLogin");
        assertThat(step.path("action").asText()).isEqualTo("assert");
        assertThat(step.path("elapsedMs").asLong()).isEqualTo(1500L);

        String allLogs = String.join("\n", logBodies);
        assertThat(allLogs).contains("executing command");
        assertThat(allLogs).contains("process exited with code 0");
        assertThat(allLogs).contains("hello from script");
        assertThat(allLogs).contains("task=task-ok");
        assertThat(allLogs).contains("serial=emulator-5554");
        assertThat(allLogs).contains("executor=SCRIPT");

        Path taskDir = properties.dataPath().resolve("tasks").resolve("task-ok");
        assertThat(Files.isRegularFile(taskDir.resolve("bundle").resolve("run.sh"))).isTrue();
        assertThat(Files.isRegularFile(taskDir.resolve("output").resolve("TEST-login.xml"))).isTrue();
        assertThat(Files.isRegularFile(taskDir.resolve("task.log"))).isTrue();
    }

    @Test
    void reportsScriptFailedWithFailedCaseNames() throws Exception {
        TaskDispatchRequest request = request("task-fail", baseUrl + "/bundle-fail");
        executor.execute(new AgentTask("task-fail", "SCRIPT"), request);

        JsonNode result = mapper.readTree(resultBody.get());
        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("reasonCategory").asText()).isEqualTo("SCRIPT_FAILED");
        assertThat(result.path("summary").asText()).contains("com.testhub.LoginTest.testBad");

        assertThat(stepBodies).hasSize(2);
        JsonNode passedStep = mapper.readTree(stepBodies.get(0));
        JsonNode failedStep = mapper.readTree(stepBodies.get(1));
        assertThat(passedStep.path("status").asText()).isEqualTo("OK");
        assertThat(failedStep.path("status").asText()).isEqualTo("FAIL");
        assertThat(failedStep.path("decision").asText()).isEqualTo("com.testhub.LoginTest.testBad");
    }

    private TaskDispatchRequest request(String taskId, String bundleUrl) {
        return new TaskDispatchRequest(taskId, "SCRIPT", "case-1", "demo case", null,
                new TaskDispatchRequest.Script("script-1", "TESTNG", "bundle.zip",
                        "sh run.sh", bundleUrl),
                new TaskDispatchRequest.Device("emulator-5554", "android", List.of("android")),
                null,
                new TaskDispatchRequest.Timeouts(30, 10),
                null,
                new TaskDispatchRequest.Callbacks(baseUrl + "/steps", baseUrl + "/logs",
                        baseUrl + "/result", "test-token"));
    }

    private byte[] okBundle() throws IOException {
        String script = """
                #!/bin/sh
                echo "hello from script"
                echo "task=$TESTHUB_TASK_ID serial=$TESTHUB_DEVICE_SERIAL executor=$TESTHUB_EXECUTOR"
                mkdir -p "$TESTHUB_OUTPUT_DIR"
                cat > "$TESTHUB_OUTPUT_DIR/TEST-login.xml" <<'XML'
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="LoginTest">
                  <testcase classname="com.testhub.LoginTest" name="testLogin" time="1.5"/>
                </testsuite>
                XML
                exit 0
                """;
        return zipOf(script);
    }

    private byte[] failingBundle() throws IOException {
        String script = """
                #!/bin/sh
                echo "about to fail"
                mkdir -p "$TESTHUB_OUTPUT_DIR"
                cat > "$TESTHUB_OUTPUT_DIR/junit.xml" <<'XML'
                <testsuite>
                  <testcase classname="com.testhub.LoginTest" name="testLogin" time="0.2"/>
                  <testcase classname="com.testhub.LoginTest" name="testBad" time="0.3">
                    <failure message="nope"/>
                  </testcase>
                </testsuite>
                XML
                echo "done"
                """;
        return zipOf(script);
    }

    private byte[] zipOf(String script) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("run.sh"));
            zip.write(script.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }

    private void serveBundle(HttpExchange exchange, byte[] zip) throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        exchange.sendResponseHeaders(200, zip.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(zip);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }
}
