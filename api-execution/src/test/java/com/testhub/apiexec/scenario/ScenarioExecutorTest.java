package com.testhub.apiexec.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.testhub.apiexec.assertion.AssertionEvaluator;
import com.testhub.apiexec.execution.HttpExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioExecutorTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/login", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            json(exchange, body.contains("\"tester\"")
                    ? "{\"token\":\"abc123\",\"user\":{\"id\":7}}" : "{\"error\":\"bad\"}", 200);
        });
        server.createContext("/profile", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            json(exchange, "Bearer abc123".equals(auth)
                    ? "{\"id\":7,\"name\":\"tester\"}" : "{\"error\":\"unauthorized\"}",
                    "Bearer abc123".equals(auth) ? 200 : 401);
        });
        server.start();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:scenario;DB_CLOSE_DELAY=-1", "sa", "")) {
            connection.createStatement().execute("create table if not exists users(id int primary key, name varchar(50))");
            connection.createStatement().execute("merge into users key(id) values (7, 'tester')");
        }
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Test
    void executesHttpSqlGroovyAndDataPassing() {
        ScenarioExecutor executor = new ScenarioExecutor(new ScenarioYamlParser(), new HttpExecutor(),
                new AssertionEvaluator(new ObjectMapper()), new ObjectMapper());
        String yaml = """
                name: login-and-query
                variables:
                  username: tester
                environment:
                  baseUrl: http://127.0.0.1:%d
                  datasources:
                    main:
                      url: jdbc:h2:mem:scenario;DB_CLOSE_DELAY=-1
                      username: sa
                      password: ''
                steps:
                  - name: login
                    type: http
                    method: POST
                    path: /login
                    body:
                      username: "${username}"
                    extract:
                      token: $.token
                      userId: $.user.id
                    assertions:
                      - type: STATUS_CODE
                        operator: EQ
                        expected: 200
                      - type: JSON_PATH
                        path: $.user.id
                        operator: EQ
                        expected: 7
                  - name: query user
                    type: sql
                    datasource: main
                    query: SELECT id AS id, name AS name FROM users WHERE name = ?
                    params: ["${username}"]
                    extract:
                      dbId: rows[0].id
                    assertions:
                      - type: SQL_ROW_COUNT
                        operator: GT
                        expected: 0
                      - type: SQL_VALUE
                        path: rows[0].name
                        operator: EQ
                        expected: tester
                  - name: compose
                    type: groovy
                    script: |
                      vars.put("combined", vars.get("token") + "-" + vars.get("dbId"))
                  - name: check composed
                    type: assert
                    assertions:
                      - type: VARIABLE
                        path: combined
                        operator: CONTAINS
                        expected: "abc123-7"
                  - name: profile
                    type: http
                    method: GET
                    path: /profile
                    headers:
                      Authorization: "Bearer ${token}"
                    assertions:
                      - type: STATUS_CODE
                        operator: EQ
                        expected: 200
                      - type: JSON_PATH
                        path: $.name
                        operator: EQ
                        expected: tester
                """.formatted(port);
        Map<String, Object> env = new LinkedHashMap<>();
        ScenarioRunResult result = executor.execute(yaml, env, Map.of(), 30, () -> false);
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.steps()).hasSize(5);
        assertThat(result.steps().get(0).extracted()).containsEntry("token", "abc123");
        assertThat(result.steps().get(1).extracted()).containsEntry("dbId", 7);
        assertThat(String.valueOf(result.variables().get("combined"))).isEqualTo("abc123-7");
        assertThat(result.steps().get(4).assertions()).allMatch(a -> a.passed());
    }


    @Test
    void executesIfElseAndDatasetLoop() {
        ScenarioExecutor executor = new ScenarioExecutor(new ScenarioYamlParser(), new HttpExecutor(),
                new AssertionEvaluator(new ObjectMapper()), new ObjectMapper());
        String yaml = """
                name: controller-flow
                datasets:
                  users:
                    - {username: tester}
                    - {username: admin}
                steps:
                  - name: set branch
                    type: set
                    variables:
                      flag: "yes"
                  - name: branch
                    type: if
                    condition: "${flag} == yes"
                    steps:
                      - name: branch-ok
                        type: assert
                        assertions:
                          - {type: VARIABLE, path: flag, operator: EQ, expected: "yes"}
                    elseSteps:
                      - name: branch-wrong
                        type: assert
                        assertions:
                          - {type: VARIABLE, path: flag, operator: EQ, expected: "no"}
                  - name: iterate users
                    type: loop
                    dataset: users
                    indexVar: idx
                    itemVar: row
                    steps:
                      - name: prepare
                        type: set
                        variables:
                          current: "${username}"
                      - name: check
                        type: assert
                        assertions:
                          - {type: VARIABLE, path: current, operator: EQ, expected: "${username}"}
                  - name: last row
                    type: assert
                    assertions:
                      - {type: VARIABLE, path: current, operator: EQ, expected: admin}
                """;
        ScenarioRunResult result = executor.execute(yaml, Map.of(), Map.of(), 30, () -> false);
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.steps()).hasSize(9);
        assertThat(result.variables().get("current")).isEqualTo("admin");
    }


    @Test
    void executesOnceAndWhileControllers() {
        ScenarioExecutor executor = new ScenarioExecutor(new ScenarioYamlParser(), new HttpExecutor(),
                new AssertionEvaluator(new ObjectMapper()), new ObjectMapper());
        String yaml = """
                name: loop-controllers
                steps:
                  - name: init
                    type: set
                    variables:
                      i: 0
                  - name: once
                    type: once
                    steps:
                      - name: once-set
                        type: set
                        variables:
                          onceValue: ok
                  - name: while
                    type: while
                    condition: "${i} < 3"
                    maxIterations: 10
                    steps:
                      - name: increment
                        type: groovy
                        script: |
                          vars.put("i", ((Number) vars.get("i")).intValue() + 1)
                  - name: verify
                    type: assert
                    assertions:
                      - {type: VARIABLE, path: i, operator: EQ, expected: 3}
                      - {type: VARIABLE, path: onceValue, operator: EQ, expected: ok}
                """;
        ScenarioRunResult result = executor.execute(yaml, Map.of(), Map.of(), 30, () -> false);
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.variables().get("i")).isEqualTo(3);
        assertThat(result.variables().get("onceValue")).isEqualTo("ok");
    }

    @Test
    void assertionFailureStopsScenarioByDefault() {
        ScenarioExecutor executor = new ScenarioExecutor(new ScenarioYamlParser(), new HttpExecutor(),
                new AssertionEvaluator(new ObjectMapper()), new ObjectMapper());
        String yaml = """
                name: failing
                environment:
                  baseUrl: http://127.0.0.1:%d
                steps:
                  - name: login
                    type: http
                    method: POST
                    path: /login
                    body: {username: tester}
                    extract:
                      token: $.token
                  - name: wrong assertion
                    type: assert
                    assertions:
                      - type: VARIABLE
                        path: token
                        operator: EQ
                        expected: nope
                  - name: never
                    type: wait
                    ms: 10
                """.formatted(port);
        ScenarioRunResult result = executor.execute(yaml, Map.of(), Map.of(), 30, () -> false);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.steps()).hasSize(2);
        assertThat(result.steps().get(1).assertions()).anyMatch(a -> !a.passed());
    }

    private static void json(HttpExchange exchange, String body, int status) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
