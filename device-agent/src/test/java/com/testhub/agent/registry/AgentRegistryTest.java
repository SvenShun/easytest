package com.testhub.agent.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.testhub.agent.config.AgentProperties;
import com.testhub.agent.driver.DeviceManager;
import com.testhub.agent.driver.ScreenRenderer;
import com.testhub.agent.http.HttpJsonClient;

/** Registration/heartbeat protocol against a loopback fake ui-execution. */
class AgentRegistryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> registerBodies = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> heartbeatBody = new AtomicReference<>();

    private HttpServer server;
    private AgentRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ui-execution/agents/register", exchange -> {
            registerBodies.add(readBody(exchange));
            respond(exchange, 200, "{\"agentId\":\"a1\",\"devices\":[]}");
        });
        server.createContext("/api/v1/ui-execution/agents/a1/heartbeat", exchange -> {
            heartbeatBody.set(readBody(exchange));
            respond(exchange, 200, "{\"agentId\":\"a1\",\"status\":\"UP\",\"devices\":[]}");
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        AgentProperties properties = new AgentProperties();
        properties.setUiExecutionUrl(baseUrl);
        properties.setRegistrationEnabled(true);
        properties.setName("device-agent-1");
        properties.setHost("127.0.0.1");
        properties.setBaseUrl("http://127.0.0.1:8090");

        DeviceManager deviceManager = new DeviceManager(properties, new ScreenRenderer());
        registry = new AgentRegistry(properties, deviceManager, new HttpJsonClient(mapper));
    }

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void registersAndSendsHeartbeat() throws Exception {
        registry.onApplicationReady();
        awaitAgentId();

        assertThat(registerBodies).hasSize(1);
        JsonNode register = mapper.readTree(registerBodies.get(0));
        assertThat(register.path("name").asText()).isEqualTo("device-agent-1");
        assertThat(register.path("host").asText()).isEqualTo("127.0.0.1");
        assertThat(register.path("baseUrl").asText()).isEqualTo("http://127.0.0.1:8090");
        assertThat(register.path("devices")).hasSize(2);
        assertThat(register.path("devices").get(0).path("serial").asText())
                .isEqualTo("emulator-5554");
        assertThat(register.path("devices").get(0).path("online").asBoolean()).isTrue();

        registry.heartbeat();
        assertThat(heartbeatBody.get()).isNotNull();
        JsonNode heartbeat = mapper.readTree(heartbeatBody.get());
        assertThat(heartbeat.path("devices")).hasSize(2);
        assertThat(registry.isUiExecutionReachable()).isTrue();
        assertThat(registry.getAgentId()).isEqualTo("a1");
    }

    @Test
    void unreachableUiExecutionDoesNotThrow() {
        // Point at a closed port; registration must only log and retry in background.
        AgentProperties properties = new AgentProperties();
        properties.setUiExecutionUrl("http://127.0.0.1:1");
        properties.setRegistrationEnabled(true);
        DeviceManager deviceManager = new DeviceManager(properties, new ScreenRenderer());
        AgentRegistry failing = new AgentRegistry(properties, deviceManager,
                new HttpJsonClient(mapper));
        try {
            failing.onApplicationReady();
            assertThat(failing.getAgentId()).isNull();
            assertThat(failing.isUiExecutionReachable()).isFalse();
        } finally {
            failing.shutdown();
        }
    }

    private void awaitAgentId() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (registry.getAgentId() != null) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("agent was not registered in time");
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
