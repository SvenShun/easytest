package com.testhub.agent.driver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class SonicUnicodeMultiTouchTest {

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", exchange -> json(exchange, "{\"value\":{\"ready\":true}}"));
        server.createContext("/session", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            if ("DELETE".equals(exchange.getRequestMethod())) {
                json(exchange, "{\"value\":null}");
            } else {
                json(exchange, "{\"value\":{\"sessionId\":\"fake-session\",\"capabilities\":{}}}");
            }
        });
        server.createContext("/session/fake-session/keys", exchange -> {
            requests.add("KEYS " + body(exchange));
            json(exchange, "{\"value\":null}");
        });
        server.createContext("/session/fake-session/touch/perform", exchange -> {
            requests.add("TOUCH " + body(exchange));
            json(exchange, "{\"value\":null}");
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsUnicodeAndTwoFingerPointerActions() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        SonicAndroidDeviceDriver driver = new SonicAndroidDeviceDriver(
                new DeviceDescriptor("serial", "Fake", "Android", "android", List.of(), true), url, "adb");

        DeviceAction text = new DeviceAction("text");
        text.setText("中文登录");
        assertThat(driver.action(text).ok()).isTrue();

        DeviceAction touch = new DeviceAction("multitouch");
        touch.setFingers(List.of(
                new DeviceAction.Finger(new DeviceAction.Point(400, 900), new DeviceAction.Point(200, 900), 300),
                new DeviceAction.Finger(new DeviceAction.Point(500, 900), new DeviceAction.Point(700, 900), 300)));
        assertThat(driver.action(touch).ok()).isTrue();

        assertThat(requests.stream().anyMatch(r -> r.startsWith("KEYS") && r.contains("中文登录"))).isTrue();
        String payload = requests.stream().filter(r -> r.startsWith("TOUCH")).findFirst().orElse("");
        assertThat(payload).contains("finger0").contains("finger1").contains("pointerMove");
        driver.close();
    }

    private static String body(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void json(HttpExchange exchange, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
