package com.testhub.agent.driver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Best-effort ADB-backed device driver. V1 does not ship real devices, but the
 * driver keeps the same device contract so a machine with adb can be used
 * without changing the rest of the agent.
 */
public class AdbDeviceDriver implements DeviceDriver {

    private static final Logger log = LoggerFactory.getLogger(AdbDeviceDriver.class);
    private static final Pattern BOUNDS = Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private final DeviceDescriptor descriptor;
    private final String adb;

    public AdbDeviceDriver(DeviceDescriptor descriptor, String adb) {
        this.descriptor = descriptor;
        this.adb = adb == null || adb.isBlank() ? "adb" : adb;
    }

    @Override
    public DeviceDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public byte[] screenshot() {
        AdbResult result = run(DEFAULT_TIMEOUT, "exec-out", "screencap", "-p");
        if (result.exitCode() != 0 || result.stdout().length == 0) {
            throw new IllegalStateException("adb screencap failed: " + result.errorText());
        }
        return result.stdout();
    }

    @Override
    public UiTree uiTree() {
        AdbResult dump = run(DEFAULT_TIMEOUT, "shell", "uiautomator", "dump", "/sdcard/window_dump.xml");
        AdbResult cat = run(DEFAULT_TIMEOUT, "exec-out", "cat", "/sdcard/window_dump.xml");
        if (cat.exitCode() != 0 || cat.stdout().length == 0) {
            log.warn("uiautomator dump failed for {} ({}, {})", descriptor.serial(),
                    dump.errorText(), cat.errorText());
            return new UiTree(1080, 1920, new Screen("android", "unknown", List.of(), List.of()));
        }
        try {
            return parseUiTree(cat.stdout());
        } catch (Exception e) {
            log.warn("cannot parse uiautomator dump for {}: {}", descriptor.serial(), e.getMessage());
            return new UiTree(1080, 1920, new Screen("android", "unknown", List.of(), List.of()));
        }
    }

    @Override
    public ActionResult action(DeviceAction request) {
        if (request == null || request.getAction() == null || request.getAction().isBlank()) {
            return ActionResult.fail("unsupported action: <empty>", "adb");
        }
        String action = request.getAction().trim().toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "click", "tap" -> {
                    UiElement element = findElement(request.getTarget());
                    if (element == null) {
                        return ActionResult.fail("element not found: " + describe(request.getTarget()), "adb");
                    }
                    Bounds bounds = element.bounds();
                    AdbResult result = run(DEFAULT_TIMEOUT, "shell", "input", "tap",
                            String.valueOf(bounds.centerX()), String.valueOf(bounds.centerY()));
                    return toResult(result, "clicked " + element.text());
                }
                case "input", "type", "settext" -> {
                    UiElement element = findElement(request.getTarget());
                    if (element == null) {
                        return ActionResult.fail("element not found: " + describe(request.getTarget()), "adb");
                    }
                    Bounds bounds = element.bounds();
                    run(DEFAULT_TIMEOUT, "shell", "input", "tap",
                            String.valueOf(bounds.centerX()), String.valueOf(bounds.centerY()));
                    String value = request.getText() == null ? "" : request.getText();
                    AdbResult result = run(DEFAULT_TIMEOUT, "shell", "input", "text", escapeText(value));
                    return toResult(result, "input " + element.text());
                }
                case "swipe" -> {
                    DeviceAction.Point from = request.getFrom() == null
                            ? new DeviceAction.Point(540, 1500) : request.getFrom();
                    DeviceAction.Point to = request.getTo() == null
                            ? new DeviceAction.Point(540, 500) : request.getTo();
                    int duration = request.getDurationMs() == null ? 300 : request.getDurationMs();
                    AdbResult result = run(DEFAULT_TIMEOUT, "shell", "input", "swipe",
                            String.valueOf(from.getX()), String.valueOf(from.getY()),
                            String.valueOf(to.getX()), String.valueOf(to.getY()),
                            String.valueOf(duration));
                    return toResult(result, "swiped");
                }
                case "back" -> {
                    return toResult(run(DEFAULT_TIMEOUT, "shell", "input", "keyevent", "4"), "pressed back");
                }
                case "home" -> {
                    return toResult(run(DEFAULT_TIMEOUT, "shell", "input", "keyevent", "3"), "pressed home");
                }
                case "launch" -> {
                    String app = request.getApp() == null || request.getApp().isBlank()
                            ? "com.testhub.demo" : request.getApp();
                    return toResult(run(DEFAULT_TIMEOUT, "shell", "monkey", "-p", app,
                            "-c", "android.intent.category.LAUNCHER", "1"), "launched " + app);
                }
                case "wait" -> {
                    int ms = request.getMs() == null ? 200 : Math.max(0, Math.min(request.getMs(), 10_000));
                    Thread.sleep(ms);
                    return ActionResult.ok("waited " + ms + "ms", "adb");
                }
                default -> {
                    return ActionResult.fail("unsupported action: " + request.getAction(), "adb");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResult.fail("action interrupted", "adb");
        } catch (Exception e) {
            return ActionResult.fail("adb action failed: " + e.getMessage(), "adb");
        }
    }

    @Override
    public ActionResult installApk(byte[] apk, boolean replace) {
        if (apk == null || apk.length == 0) {
            return ActionResult.fail("empty apk", "adb");
        }
        java.nio.file.Path temp = null;
        try {
            temp = java.nio.file.Files.createTempFile("testhub-", ".apk");
            java.nio.file.Files.write(temp, apk);
            List<String> args = new ArrayList<>(List.of("install"));
            if (replace) {
                args.add("-r");
            }
            args.add(temp.toAbsolutePath().toString());
            AdbResult result = run(Duration.ofSeconds(120), args.toArray(String[]::new));
            return toResult(result, "installed apk (" + apk.length + " bytes)");
        } catch (Exception e) {
            return ActionResult.fail("adb install failed: " + e.getMessage(), "adb");
        } finally {
            if (temp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(temp);
                } catch (Exception ignored) {
                    // best effort
                }
            }
        }
    }

    @Override
    public ActionResult reset() {
        run(DEFAULT_TIMEOUT, "shell", "input", "keyevent", "3");
        run(DEFAULT_TIMEOUT, "shell", "am", "force-stop", "com.testhub.demo");
        return ActionResult.ok("reset", "home");
    }

    // ------------------------------------------------------------------
    // discovery / command execution
    // ------------------------------------------------------------------

    /** Discover devices via {@code adb devices -l}; returns an empty list if adb is missing. */
    public static List<DeviceDescriptor> discover(String adbExecutable) {
        String adb = adbExecutable == null || adbExecutable.isBlank() ? "adb" : adbExecutable;
        List<DeviceDescriptor> devices = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(adb, "devices", "-l");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("adb devices timed out");
                return devices;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("List of devices")
                        || trimmed.startsWith("* daemon")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 2 || !"device".equals(parts[1])) {
                    continue;
                }
                String serial = parts[0];
                String model = null;
                for (String part : parts) {
                    if (part.startsWith("model:")) {
                        model = part.substring("model:".length());
                    }
                }
                String name = model == null ? serial : model.replace('_', ' ');
                devices.add(new DeviceDescriptor(serial, name, model == null ? serial : model,
                        "android", List.of("android", "adb"), true));
            }
        } catch (Exception e) {
            log.warn("adb discovery unavailable: {}", e.getMessage());
        }
        return devices;
    }

    private UiTree parseUiTree(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));
        Element root = document.getDocumentElement();
        String pkg = root.getAttribute("package");
        NodeList nodes = document.getElementsByTagName("node");
        Bounds rootBounds = parseBounds(root.getAttribute("bounds"));
        if (rootBounds == null && nodes.getLength() > 0) {
            rootBounds = parseBounds(((Element) nodes.item(0)).getAttribute("bounds"));
        }
        int width = rootBounds == null ? 1080 : rootBounds.width();
        int height = rootBounds == null ? 1920 : rootBounds.height();

        List<UiElement> elements = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int generated = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            String text = node.getAttribute("text");
            String resourceId = node.getAttribute("resource-id");
            String className = node.getAttribute("class");
            boolean clickable = "true".equalsIgnoreCase(node.getAttribute("clickable"));
            boolean editable = className != null && className.contains("EditText");
            Bounds bounds = parseBounds(node.getAttribute("bounds"));
            if (bounds == null) {
                continue;
            }
            if (!clickable && !editable) {
                if (text != null && !text.isBlank() && texts.size() < 20) {
                    texts.add(text);
                }
                continue;
            }
            String id = resourceId == null || resourceId.isBlank()
                    ? "node-" + (generated++) : resourceId.substring(resourceId.lastIndexOf('/') + 1);
            String type = editable ? "editable" : (className != null && className.contains("Button") ? "button" : "text");
            elements.add(new UiElement(id, text, type, clickable, editable, bounds));
        }
        String title = texts.isEmpty() ? pkg : texts.get(0);
        return new UiTree(width, height, new Screen(pkg, title, texts, elements));
    }

    private static Bounds parseBounds(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = BOUNDS.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        int left = Integer.parseInt(matcher.group(1));
        int top = Integer.parseInt(matcher.group(2));
        int right = Integer.parseInt(matcher.group(3));
        int bottom = Integer.parseInt(matcher.group(4));
        return new Bounds(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    private UiElement findElement(DeviceAction.Target target) {
        UiTree tree = uiTree();
        List<UiElement> elements = tree.screen().elements();
        if (target == null) {
            return null;
        }
        if (target.getId() != null && !target.getId().isBlank()) {
            for (UiElement element : elements) {
                if (target.getId().equals(element.id())) {
                    return element;
                }
            }
        }
        if (target.getText() != null && !target.getText().isBlank()) {
            for (UiElement element : elements) {
                if (target.getText().equals(element.text())) {
                    return element;
                }
            }
            for (UiElement element : elements) {
                if (element.text() != null && element.text().contains(target.getText())) {
                    return element;
                }
            }
        }
        if (target.getIndex() != null) {
            int index = target.getIndex();
            if (index >= 0 && index < elements.size()) {
                return elements.get(index);
            }
        }
        return null;
    }

    private ActionResult toResult(AdbResult result, String message) {
        if (result.exitCode() == 0) {
            return ActionResult.ok(message, "adb");
        }
        return ActionResult.fail(message + " failed: " + result.errorText(), "adb");
    }

    private AdbResult run(Duration timeout, String... args) {
        List<String> command = new ArrayList<>();
        command.add(adb);
        command.add("-s");
        command.add(descriptor.serial());
        command.addAll(List.of(args));
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread outReader = pipe(process.getInputStream(), stdout);
            Thread errReader = pipe(process.getErrorStream(), stderr);
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            outReader.join(2000);
            errReader.join(2000);
            int exitCode = finished ? process.exitValue() : 124;
            return new AdbResult(exitCode, stdout.toByteArray(),
                    new String(stderr.toByteArray(), StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            return new AdbResult(1, new byte[0], e.getMessage());
        }
    }

    private static Thread pipe(InputStream input, ByteArrayOutputStream output) {
        Thread thread = new Thread(() -> {
            try (input; output) {
                input.transferTo(output);
            } catch (IOException ignored) {
                // best effort
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static String escapeText(String value) {
        // adb input text uses %s for spaces.
        return value.replace(" ", "%s");
    }

    private static String describe(DeviceAction.Target target) {
        return target == null ? "<empty>" : target.describe();
    }

    private record AdbResult(int exitCode, byte[] stdout, String errorText) {
    }
}
