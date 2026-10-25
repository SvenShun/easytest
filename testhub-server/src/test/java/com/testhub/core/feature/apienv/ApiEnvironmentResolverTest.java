package com.testhub.core.feature.apienv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.testhub.common.web.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiEnvironmentResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void resolvesVariablesHeadersAndLoginToken() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!body.contains("tester")) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            byte[] raw = "{\"data\":{\"token\":\"tok-123\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, raw.length);
            exchange.getResponseBody().write(raw);
            exchange.close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        ApiEnvironmentDtos.AuthConfig auth = new ApiEnvironmentDtos.AuthConfig(
                "LOGIN", null, null, null, null, null, null,
                "POST", "/login", Map.of("Content-Type", "application/json"),
                "{\"username\":\"${user}\",\"password\":\"secret\"}",
                "$.data.token", "X-Token", "Token ");
        ApiEnvironment env = new ApiEnvironment("env-1", "测试环境", baseUrl,
                mapper.writeValueAsString(Map.of("X-Common", "${user}")),
                mapper.writeValueAsString(Map.of("user", "tester")),
                "LOGIN", mapper.writeValueAsString(auth), true, "tester", Instant.now(), Instant.now());

        ApiEnvironmentResolver resolver = new ApiEnvironmentResolver(mapper);
        ApiEnvironmentResolver.ResolvedEnvironment resolved = resolver.resolve(env);

        assertThat(resolved.baseUrl()).isEqualTo(baseUrl);
        assertThat(resolved.headers()).containsEntry("X-Common", "tester");
        assertThat(resolved.headers()).containsEntry("X-Token", "Token tok-123");
        assertThat(resolver.apply("${user}-${data.id}", resolved, Map.of("id", 7))).isEqualTo("tester-7");
    }

    @Test
    void rejectsMissingVariable() {
        ApiEnvironment env = new ApiEnvironment("env-2", "空环境", "http://127.0.0.1:1",
                "{}", "{}", "NONE", "{}", true, "tester", Instant.now(), Instant.now());
        ApiEnvironmentResolver resolver = new ApiEnvironmentResolver(mapper);
        ApiEnvironmentResolver.ResolvedEnvironment resolved = resolver.resolve(env);

        assertThatThrownBy(() -> resolver.apply("${missing}", resolved, Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("变量未定义");
    }
}
