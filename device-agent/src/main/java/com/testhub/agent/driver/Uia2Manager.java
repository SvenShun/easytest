package com.testhub.agent.driver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Installs and starts Appium UIA2 server (the same APKs Sonic-Agent bundles),
 * forwards a loopback port and swaps the device to Sonic's UIA2 driver.
 */
@Component
public class Uia2Manager {

    private static final String SERVER_PKG = "io.appium.uiautomator2.server";
    private static final String TEST_PKG = "io.appium.uiautomator2.server.test";
    private static final String RUNNER = TEST_PKG + "/androidx.test.runner.AndroidJUnitRunner";

    private final DeviceManager devices;
    private final String adb;
    private final String serverApk;
    private final String testApk;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Uia2Manager(DeviceManager devices,
                       @Value("${testhub.agent.uia2-server-apk:tools/uia2/sonic-appium-uiautomator2-server.apk}") String serverApk,
                       @Value("${testhub.agent.uia2-test-apk:tools/uia2/sonic-appium-uiautomator2-server-test.apk}") String testApk) {
        this.devices = devices;
        this.adb = devices.adb();
        this.serverApk = serverApk;
        this.testApk = testApk;
    }

    public synchronized Map<String, Object> start(String serial) {
        Session existing = sessions.get(serial);
        if (existing != null && existing.driver != null) {
            return Map.of("ok", true, "uiaUrl", existing.uiaUrl, "port", existing.port,
                    "message", "UIA2 已在运行");
        }
        Optional<DeviceDescriptor> descriptor = devices.descriptor(serial);
        if (descriptor.isEmpty()) {
            throw new IllegalStateException("设备不存在: " + serial);
        }
        ensureInstalled(serial);
        int port = freePort();
        Process instrument = null;
        try {
            exec(serial, 30, "appops", "set", SERVER_PKG, "RUN_IN_BACKGROUND", "allow");
            exec(serial, 30, "appops", "set", TEST_PKG, "RUN_IN_BACKGROUND", "allow");
            exec(serial, 60, "forward", "tcp:" + port, "tcp:6790");
            instrument = new ProcessBuilder(adb, "-s", serial, "shell", "am", "instrument", "-w", "-r",
                    "-e", "DISABLE_SUPPRESS_ACCESSIBILITY_SERVICES", "true",
                    "-e", "disableAnalytics", "true", RUNNER)
                    .redirectErrorStream(true).start();
            if (!waitReady(port, instrument, 45)) {
                throw new IllegalStateException("UIA2 启动超时，请检查设备是否允许后台运行/无障碍");
            }
            String uiaUrl = "http://127.0.0.1:" + port;
            SonicAndroidDeviceDriver driver = new SonicAndroidDeviceDriver(descriptor.get(), uiaUrl, adb);
            devices.replace(serial, driver);
            sessions.put(serial, new Session(port, uiaUrl, instrument, driver));
            return Map.of("ok", true, "uiaUrl", uiaUrl, "port", port);
        } catch (Exception e) {
            if (instrument != null) {
                instrument.destroyForcibly();
            }
            exec(serial, 15, "forward", "--remove", "tcp:" + port);
            throw new IllegalStateException("启动 UIA2 失败: " + e.getMessage(), e);
        }
    }

    public synchronized Map<String, Object> stop(String serial) {
        Session session = sessions.remove(serial);
        if (session == null) {
            return Map.of("ok", true, "message", "UIA2 未运行");
        }
        try {
            session.driver.close();
        } catch (Exception ignored) {
            // best effort
        }
        if (session.instrument != null) {
            session.instrument.destroyForcibly();
        }
        exec(serial, 15, "forward", "--remove", "tcp:" + session.port);
        exec(serial, 15, "shell", "am", "force-stop", SERVER_PKG);
        exec(serial, 15, "shell", "am", "force-stop", TEST_PKG);
        devices.restoreAdb(serial);
        return Map.of("ok", true, "message", "UIA2 已停止");
    }

    public Optional<Map<String, Object>> status(String serial) {
        Session session = sessions.get(serial);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(Map.of("ok", true, "uiaUrl", session.uiaUrl, "port", session.port,
                "instrumentAlive", session.instrument != null && session.instrument.isAlive()));
    }

    private void ensureInstalled(String serial) {
        String path = exec(serial, 20, "shell", "pm", "path", SERVER_PKG).text();
        if (path == null || !path.contains("package:")) {
            install(serial, resolve(serverApk));
            install(serial, resolve(testApk));
        } else {
            String testPath = exec(serial, 20, "shell", "pm", "path", TEST_PKG).text();
            if (testPath == null || !testPath.contains("package:")) {
                install(serial, resolve(testApk));
            }
        }
    }

    private void install(String serial, Path apk) {
        if (!Files.exists(apk)) {
            throw new IllegalStateException("UIA2 APK 不存在: " + apk
                    + "（运行 scripts/uia2-setup.sh 或设置 testhub.agent.uia2-*.apk）");
        }
        ExecResult result = exec(serial, 180, "install", "-r", "-t", apk.toAbsolutePath().toString());
        if (result.exitCode() != 0) {
            throw new IllegalStateException("安装 " + apk.getFileName() + " 失败: " + result.errorText());
        }
    }

    private Path resolve(String path) {
        Path candidate = Paths.get(path);
        if (candidate.isAbsolute()) {
            return candidate;
        }
        Path base = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && base != null; i++) {
            Path resolved = base.resolve(candidate).normalize();
            if (Files.exists(resolved)) {
                return resolved;
            }
            base = base.getParent();
        }
        return Paths.get(System.getProperty("user.dir")).resolve(candidate).normalize();
    }

    private boolean waitReady(int port, Process instrument, int timeoutSec) throws InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (instrument != null && !instrument.isAlive()) {
                return false;
            }
            for (String path : List.of("/status", "/wd/hub/status")) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .timeout(Duration.ofSeconds(1)).GET().build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 500) {
                        Thread.sleep(1200);
                        return true;
                    }
                } catch (Exception ignored) {
                    // not ready yet
                }
            }
            Thread.sleep(700);
        }
        return false;
    }

    private int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("无法分配 UIA2 端口: " + e.getMessage(), e);
        }
    }

    private ExecResult exec(String serial, int timeoutSec, String... args) {
        List<String> command = new ArrayList<>();
        command.add(adb);
        command.add("-s");
        command.add(serial);
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new ExecResult(1, new byte[0], "adb command timed out");
            }
            byte[] output = process.getInputStream().readAllBytes();
            return new ExecResult(process.exitValue(), output, "");
        } catch (Exception e) {
            return new ExecResult(1, new byte[0], e.getMessage());
        }
    }

    private record Session(int port, String uiaUrl, Process instrument, SonicAndroidDeviceDriver driver) {
    }

    private record ExecResult(int exitCode, byte[] stdout, String errorText) {
        public String text() {
            return new String(stdout, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
