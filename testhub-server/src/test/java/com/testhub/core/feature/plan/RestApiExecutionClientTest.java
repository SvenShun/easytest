package com.testhub.core.feature.plan;

import com.sun.net.httpserver.HttpServer;
import com.testhub.common.security.SecurityProperties;
import com.testhub.core.config.CoreProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestApiExecutionClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listsTasksByPlanRunAgainstAbsoluteWorkerUrl() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/api-execution/tasks", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String body = "{\"items\":[{\"taskId\":\"api-task-1\",\"status\":\"SUCCESS\","
                    + "\"failureReason\":null,\"summary\":\"ok\"}],\"total\":1,"
                    + "\"planRunId\":\"" + query + "\"}";
            byte[] raw = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, raw.length);
            exchange.getResponseBody().write(raw);
            exchange.close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        CoreProperties properties = new CoreProperties();
        properties.setApiExecutionBaseUrls(baseUrl);
        SecurityProperties security = new SecurityProperties();
        security.setServiceToken("test-token");
        RestApiExecutionClient client = new RestApiExecutionClient(properties, security);

        List<ApiExecutionClient.TaskSnapshot> tasks = client.listTasksByPlanRun("run-1");

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).taskId()).isEqualTo("api-task-1");
        assertThat(tasks.get(0).status()).isEqualTo("SUCCESS");
    }
}
