package com.testhub.agent.driver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Uia2ManagerTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startFakeUia2() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/status", exchange -> json(exchange, "{\"value\":{\"ready\":true}}"));
        server.createContext("/session", exchange -> {
            if ("DELETE".equals(exchange.getRequestMethod())) {
                json(exchange, "{\"value\":null}");
            } else {
                json(exchange, "{\"value\":{\"sessionId\":\"fake-session\",\"capabilities\":{}}}");
            }
        });
        server.start();
        System.setProperty("testhub.agent.uia2-port", String.valueOf(port));
    }

    @AfterAll
    static void stopFakeUia2() {
        server.stop(0);
        System.clearProperty("testhub.agent.uia2-port");
    }

    @Test
    void startsAndStopsUia2WithFakeAdb(@TempDir Path tempDir) throws Exception {
        Path adb = tempDir.resolve("adb");
        Files.writeString(adb, """
                #!/bin/sh
                case "$*" in
                  *"pm path io.appium.uiautomator2.server.test"*) echo "package:/data/app/test.apk" ;;
                  *"pm path io.appium.uiautomator2.server"*) echo "package:/data/app/server.apk" ;;
                  *"am instrument"*) sleep 20 ;;
                esac
                exit 0
                """);
        adb.toFile().setExecutable(true);

        DeviceManager manager = mock(DeviceManager.class);
        when(manager.adb()).thenReturn(adb.toAbsolutePath().toString());
        DeviceDescriptor descriptor = new DeviceDescriptor("serial-1", "Fake", "Android", "android",
                List.of("fake"), true);
        when(manager.descriptor("serial-1")).thenReturn(Optional.of(descriptor));

        Uia2Manager uia2 = new Uia2Manager(manager, "missing-server.apk", "missing-test.apk");
        Map<String, Object> started = uia2.start("serial-1");

        assertThat(started.get("ok")).isEqualTo(true);
        assertThat(started.get("uiaUrl")).isEqualTo("http://127.0.0.1:" + port);
        verify(manager).replace(eq("serial-1"), any(SonicAndroidDeviceDriver.class));

        Map<String, Object> stopped = uia2.stop("serial-1");
        assertThat(stopped.get("ok")).isEqualTo(true);
        verify(manager).restoreAdb("serial-1");
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
