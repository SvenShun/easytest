package com.testhub.agent.driver;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.driver.android.AndroidDriver;
import org.cloud.sonic.driver.android.enmus.AndroidSelector;
import org.cloud.sonic.driver.android.service.AndroidElement;
import org.cloud.sonic.driver.common.models.WindowSize;
import org.cloud.sonic.driver.common.tool.SonicRespException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real Android driver backed by Sonic's {@code sonic-driver-core}
 * ({@code io.github.soniccloudorg:sonic-driver-core}, Apache-2.0), the same UIA2
 * client Sonic-Agent uses. Actions are expressed in Sonic's XPATH/UIA2 terms and
 * mapped to TestHub's device-action contract.
 *
 * <p>Requires a UIA2 server reachable at {@code uiaUrl}; normally Sonic-Agent
 * starts it together with the Sonic Android UIA APK. Without that endpoint this
 * mode is not usable — {@code simulated} remains the offline default.</p>
 */
public class SonicAndroidDeviceDriver implements DeviceDriver {

    private static final Logger log = LoggerFactory.getLogger(SonicAndroidDeviceDriver.class);
    private static final Pattern BOUNDS = Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");
    private static final int MAX_ELEMENTS = 300;

    private final DeviceDescriptor descriptor;
    private final AndroidDriver driver;
    private final String adb;

    public SonicAndroidDeviceDriver(DeviceDescriptor descriptor, String uiaUrl, String adb) {
        this.descriptor = descriptor;
        this.adb = adb == null || adb.isBlank() ? "adb" : adb;
        try {
            this.driver = new AndroidDriver(uiaUrl, 30_000, new JSONObject());
            log.info("sonic driver connected serial={} uia={}", descriptor.serial(), uiaUrl);
        } catch (SonicRespException e) {
            throw new IllegalStateException("无法连接 Sonic UIA2 服务 " + uiaUrl + ": " + e.getMessage(), e);
        }
    }

    @Override
    public DeviceDescriptor descriptor() {
        return descriptor;
    }

    public void close() {
        try {
            driver.closeDriver();
        } catch (Exception ignored) {
            // best effort
        }
    }

    @Override
    public byte[] screenshot() {
        try {
            return driver.screenshot();
        } catch (SonicRespException e) {
            throw new IllegalStateException("Sonic 截图失败: " + e.getMessage(), e);
        }
    }

    @Override
    public UiTree uiTree() {
        try {
            WindowSize size = driver.getWindowSize();
            return parseUiTree(driver.getPageSource(), size.getWidth(), size.getHeight());
        } catch (SonicRespException e) {
            throw new IllegalStateException("Sonic 读取 UI 树失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ActionResult action(DeviceAction action) {
        if (action == null || action.getAction() == null) {
            return ActionResult.fail("action is required", null);
        }
        String type = action.getAction().trim().toLowerCase();
        try {
            switch (type) {
                case "click":
                    return click(action);
                case "input":
                    return input(action);
                case "text":
                    if (action.getText() == null) {
                        return ActionResult.fail("text requires text", null);
                    }
                    driver.sendKeys(action.getText(), true);
                    return ActionResult.ok("sent unicode text", null);
                case "swipe":
                    return swipe(action);
                case "multitouch":
                    return multiTouch(action);
                case "back":
                    keyEvent(4);
                    return ActionResult.ok("back", null);
                case "home":
                    keyEvent(3);
                    return ActionResult.ok("home", null);
                case "launch":
                    return launch(action.getApp());
                case "wait":
                    Thread.sleep(action.getMs() == null ? 500 : Math.max(0, action.getMs()));
                    return ActionResult.ok("waited", null);
                case "done":
                    return ActionResult.ok("done", null);
                default:
                    return ActionResult.fail("unsupported action: " + type, null);
            }
        } catch (Exception e) {
            log.debug("sonic action failed serial={} action={}", serial(), type, e);
            return ActionResult.fail(e.getMessage() == null ? e.toString() : e.getMessage(), null);
        }
    }

    @Override
    public ActionResult reset() {
        // UIA2 does not expose a reset verb; restarting the app under test is
        // driver/application specific, so reset is a no-op for real devices.
        return ActionResult.ok("sonic driver: reset is not supported", null);
    }

    private ActionResult click(DeviceAction action) throws SonicRespException {
        DeviceAction.Target target = action.getTarget();
        if (target != null && target.getId() != null && !target.getId().isBlank()) {
            element("//*[@resource-id=\"" + escape(target.getId()) + "\"]").click();
            return ActionResult.ok("clicked id " + target.getId(), null);
        }
        if (target != null && target.getText() != null && !target.getText().isBlank()) {
            String text = escape(target.getText());
            try {
                element("//*[@text=\"" + text + "\"]").click();
            } catch (SonicRespException notFound) {
                element("//*[contains(@text,\"" + text + "\")]").click();
            }
            return ActionResult.ok("clicked " + target.getText(), null);
        }
        return ActionResult.fail("click requires target.text or target.id", null);
    }

    private ActionResult input(DeviceAction action) throws SonicRespException {
        DeviceAction.Target target = action.getTarget();
        if (action.getText() == null) {
            return ActionResult.fail("input requires text", null);
        }
        String xpath;
        if (target != null && target.getId() != null && !target.getId().isBlank()) {
            xpath = "//*[@resource-id=\"" + escape(target.getId()) + "\"]";
        } else if (target != null && target.getText() != null && !target.getText().isBlank()) {
            xpath = "//android.widget.EditText[@text=\"" + escape(target.getText()) + "\"]";
        } else {
            int index = target == null || target.getIndex() == null ? 0 : target.getIndex();
            List<AndroidElement> editables = driver.findElementList(AndroidSelector.XPATH,
                    "//android.widget.EditText", 3, 500);
            if (editables.size() <= index) {
                return ActionResult.fail("editable input not found: index " + index, null);
            }
            editables.get(index).sendKeys(action.getText());
            return ActionResult.ok("input text at index " + index, null);
        }
        element(xpath).sendKeys(action.getText());
        return ActionResult.ok("input " + action.getText(), null);
    }

    private ActionResult multiTouch(DeviceAction action) {
        if (action.getFingers() == null || action.getFingers().size() < 2) {
            return ActionResult.fail("multitouch 至少需要两根手指", null);
        }
        try {
            var uia = driver.getUiaClient();
            String base = uia.getRemoteUrl();
            String session = driver.getSessionId();
            if (base == null || base.isBlank() || session == null || session.isBlank()) {
                return ActionResult.fail("UIA2 session 不可用", null);
            }
            com.alibaba.fastjson.JSONArray pointers = new com.alibaba.fastjson.JSONArray();
            int index = 0;
            for (DeviceAction.Finger finger : action.getFingers()) {
                if (finger == null || finger.getFrom() == null || finger.getTo() == null) {
                    return ActionResult.fail("每根手指都需要 from/to", null);
                }
                com.alibaba.fastjson.JSONObject pointer = new com.alibaba.fastjson.JSONObject();
                pointer.put("type", "pointer");
                pointer.put("id", "finger" + (index++));
                pointer.put("parameters", java.util.Map.of("pointerType", "touch"));
                com.alibaba.fastjson.JSONArray steps = new com.alibaba.fastjson.JSONArray();
                steps.add(pointerStep("pointerMove", finger.getFrom(), 0));
                com.alibaba.fastjson.JSONObject down = new com.alibaba.fastjson.JSONObject();
                down.put("type", "pointerDown");
                down.put("button", 0);
                steps.add(down);
                steps.add(pointerStep("pointerMove", finger.getTo(),
                        finger.getDurationMs() == null ? 300 : finger.getDurationMs()));
                com.alibaba.fastjson.JSONObject up = new com.alibaba.fastjson.JSONObject();
                up.put("type", "pointerUp");
                up.put("button", 0);
                steps.add(up);
                pointer.put("actions", steps);
                pointers.add(pointer);
            }
            com.alibaba.fastjson.JSONObject body = new com.alibaba.fastjson.JSONObject();
            body.put("actions", pointers);
            String url = base.replaceAll("/+$", "") + "/session/" + session + "/touch/perform";
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .build();
            java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return ActionResult.fail("UIA2 多点触控失败: HTTP " + response.statusCode()
                        + " " + response.body(), null);
            }
            return ActionResult.ok("multitouch " + action.getFingers().size() + " fingers", null);
        } catch (Exception e) {
            return ActionResult.fail("多点触控失败: " + e.getMessage(), null);
        }
    }

    private com.alibaba.fastjson.JSONObject pointerStep(String type, DeviceAction.Point point, int durationMs) {
        com.alibaba.fastjson.JSONObject step = new com.alibaba.fastjson.JSONObject();
        step.put("type", type);
        step.put("duration", durationMs);
        step.put("x", point.getX());
        step.put("y", point.getY());
        step.put("origin", "viewport");
        return step;
    }

    private ActionResult swipe(DeviceAction action) {
        if (action.getFrom() == null || action.getTo() == null) {
            return ActionResult.fail("swipe requires from/to", null);
        }
        try {
            driver.swipe(action.getFrom().getX(), action.getFrom().getY(),
                    action.getTo().getX(), action.getTo().getY(),
                    action.getDurationMs() == null ? 300 : action.getDurationMs());
            return ActionResult.ok("swiped", null);
        } catch (SonicRespException e) {
            return ActionResult.fail("swipe failed: " + e.getMessage(), null);
        }
    }

    private ActionResult launch(String app) {
        if (app == null || app.isBlank()) {
            return ActionResult.fail("launch requires app package", null);
        }
        try {
            Process process = new ProcessBuilder(adb, "-s", serial(), "shell", "monkey",
                    "-p", app, "-c", "android.intent.category.LAUNCHER", "1")
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ActionResult.fail("launch timed out", null);
            }
            return process.exitValue() == 0
                    ? ActionResult.ok("launched " + app, null)
                    : ActionResult.fail("launch failed: " + app, null);
        } catch (Exception e) {
            return ActionResult.fail("launch failed: " + e.getMessage(), null);
        }
    }

    private void keyEvent(int keyCode) throws Exception {
        Process process = new ProcessBuilder(adb, "-s", serial(), "shell", "input", "keyevent",
                String.valueOf(keyCode)).redirectErrorStream(true).start();
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("adb keyevent timed out");
        }
    }

    private AndroidElement element(String xpath) throws SonicRespException {
        return driver.findElement(AndroidSelector.XPATH, xpath, 3, 500);
    }

    /** Parses a uiautomator page source into TestHub's UI tree contract. */
    static UiTree parseUiTree(String xml, int width, int height) {
        List<UiElement> elements = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = document.getElementsByTagName("node");
            for (int i = 0; i < nodes.getLength() && elements.size() < MAX_ELEMENTS; i++) {
                Element node = (Element) nodes.item(i);
                String text = node.getAttribute("text");
                String resourceId = node.getAttribute("resource-id");
                String className = node.getAttribute("class");
                boolean clickable = "true".equals(node.getAttribute("clickable"));
                Bounds bounds = parseBounds(node.getAttribute("bounds"));
                String type = className != null && className.contains("EditText") ? "editable"
                        : clickable ? "button" : "text";
                if ((text == null || text.isBlank()) && (resourceId == null || resourceId.isBlank())
                        && bounds == null) {
                    continue;
                }
                elements.add(new UiElement(resourceId, text, type, clickable,
                        "editable".equals(type), bounds));
                if (text != null && !text.isBlank() && texts.size() < 50) {
                    texts.add(text);
                }
            }
        } catch (Exception e) {
            log.warn("failed to parse uiautomator page source: {}", e.getMessage());
        }
        String title = texts.isEmpty() ? "" : texts.get(0);
        return new UiTree(width, height, new Screen("", title, texts, elements), elements);
    }

    private static Bounds parseBounds(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = BOUNDS.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        int x1 = Integer.parseInt(matcher.group(1));
        int y1 = Integer.parseInt(matcher.group(2));
        int x2 = Integer.parseInt(matcher.group(3));
        int y2 = Integer.parseInt(matcher.group(4));
        return new Bounds(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
