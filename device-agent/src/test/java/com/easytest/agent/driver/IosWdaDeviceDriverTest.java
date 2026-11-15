package com.easytest.agent.driver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IosWdaDeviceDriverTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void speaksWdaForScreenshotTreeAndTap() throws Exception {
        AtomicReference<String> tapPath = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String xml = """
                <AppiumAUT name="Demo">
                  <XCUIElementTypeButton name="登录" label="登录" type="XCUIElementTypeButton" x="100" y="200" width="300" height="100"/>
                  <XCUIElementTypeTextField name="用户名" type="XCUIElementTypeTextField" x="100" y="400" width="500" height="80"/>
                </AppiumAUT>
                """;
        server.createContext("/session", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, "{\"sessionId\":\"s1\"}");
            } else {
                respond(exchange, "{}");
            }
        });
        server.createContext("/session/s1/source", exchange -> respond(exchange, json(xml)));
        server.createContext("/session/s1/screenshot", exchange ->
                respond(exchange, "{\"value\":\"" + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}) + "\"}"));
        server.createContext("/session/s1/wda/tap", exchange -> {
            tapPath.set(exchange.getRequestURI().getPath());
            respond(exchange, "{\"value\":{}}");
        });
        server.start();

        DeviceDescriptor descriptor = new DeviceDescriptor("ios-sim-1", "iPhone", "iPhone 15", "ios",
                List.of("ios", "wda"), true);
        IosWdaDeviceDriver driver = new IosWdaDeviceDriver(descriptor, base);

        assertThat(driver.screenshot()).containsExactly(1, 2, 3);
        UiTree tree = driver.uiTree();
        assertThat(tree.elements()).extracting(UiElement::text).contains("登录", "用户名");
        ActionResult result = driver.action(DeviceAction.click("登录"));
        assertThat(result.ok()).isTrue();
        assertThat(tapPath.get()).isEqualTo("/session/s1/wda/tap");
    }

    @Test
    void reportsFailureWhenTargetIsMissing() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/session", exchange -> respond(exchange, "{\"sessionId\":\"s1\"}"));
        server.createContext("/session/s1/source", exchange -> respond(exchange,
                json("<AppiumAUT name=\"Demo\"></AppiumAUT>")));
        server.start();
        DeviceDescriptor descriptor = new DeviceDescriptor("ios-sim-2", "iPhone", "iPhone", "ios",
                List.of("ios"), true);
        IosWdaDeviceDriver driver = new IosWdaDeviceDriver(descriptor,
                "http://127.0.0.1:" + server.getAddress().getPort());
        assertThat(driver.action(DeviceAction.click("不存在")).ok()).isFalse();
    }

    private static String json(String value) {
        return "{\"value\":" + quote(value) + "}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
