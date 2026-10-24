package com.testhub.uiexec.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.testhub.common.web.ApiException;
import com.testhub.uiexec.client.dto.ExecutionContext;
import com.testhub.uiexec.client.dto.FailureAnalysisPayload;
import com.testhub.uiexec.config.UiExecProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapter contract tests against a stub of the MeterSphere v3 endpoints read
 * from upstream source:
 *   GET  /functional/case/detail/{id}
 *   POST /functional/case/comment/save
 */
class MeterSphereAdapterTest {

    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> lastCommentBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/functional/case/detail/c1", exchange -> respond(exchange, 200, """
                {"id":"c1","projectId":"p1","name":"用户使用正确凭据登录",
                 "prerequisite":"已打开 App 且未登录","textDescription":"输入 tester/secret 并点击登录",
                 "steps":"[]","expectedResult":"进入首页并显示 登录成功","reviewStatus":"COMPLETED"}
                """));
        server.createContext("/functional/case/detail/pending", exchange -> respond(exchange, 200, """
                {"id":"pending","projectId":"p1","name":"待评审用例","prerequisite":"",
                 "textDescription":"执行操作","expectedResult":"结果","reviewStatus":"PREPARED"}
                """));
        server.createContext("/functional/case/comment/save", exchange -> {
            lastCommentBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{}");
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsMeterSphereCaseToBddContext() {
        UiExecProperties properties = properties(false);
        MeterSphereCaseContextSource source = new MeterSphereCaseContextSource(properties);

        ExecutionContext context = source.fetch("c1");

        assertThat(source.backend()).isEqualTo("metersphere");
        assertThat(context.title()).isEqualTo("用户使用正确凭据登录");
        assertThat(context.reviewStatus()).isEqualTo("APPROVED");
        assertThat(context.bdd().given()).isEqualTo("已打开 App 且未登录");
        assertThat(context.bdd().when()).isEqualTo("输入 tester/secret 并点击登录");
        assertThat(context.bdd().then()).isEqualTo("进入首页并显示 登录成功");
        assertThat(context.script()).isNull();
    }

    @Test
    void reviewGateBlocksUnapprovedCase() {
        UiExecProperties properties = properties(true);
        MeterSphereCaseContextSource source = new MeterSphereCaseContextSource(properties);

        assertThat(source.fetch("c1").reviewStatus()).isEqualTo("APPROVED");
        assertThat(source.fetch("pending").reviewStatus()).isEqualTo("PREPARED");
    }

    @Test
    void mapsNotFound() {
        MeterSphereCaseContextSource source = new MeterSphereCaseContextSource(properties(false));
        assertThatThrownBy(() -> source.fetch("missing"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo("METERSPHERE_CASE_NOT_FOUND");
    }

    @Test
    void writesFailureAnalysisAsComment() throws Exception {
        UiExecProperties properties = properties(false);
        properties.getMetersphere().setProjectId("p1");
        MeterSphereFailureAnalysisSink sink = new MeterSphereFailureAnalysisSink(properties);

        sink.write("c1", new FailureAnalysisPayload("task-1", "http://reports/task-1.json",
                "模型在第 4 步无法解析动作", "MODEL_DECISION_FAILED"));

        JsonNode body = mapper.readTree(lastCommentBody.get());
        assertThat(body.get("caseId").asText()).isEqualTo("c1");
        assertThat(body.get("event").asText()).isEqualTo("COMMENT");
        assertThat(body.get("projectId").asText()).isEqualTo("p1");
        assertThat(body.get("content").asText())
                .contains("MODEL_DECISION_FAILED")
                .contains("http://reports/task-1.json")
                .contains("task-1");
    }

    private UiExecProperties properties(boolean requireReview) {
        UiExecProperties properties = new UiExecProperties();
        properties.getMetersphere().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getMetersphere().setToken("test-token");
        properties.getMetersphere().setRequireReview(requireReview);
        return properties;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
