package com.easytest.agent.driver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * iOS driver backed by WebDriverAgent (WDA). The same platform-neutral
 * {@link DeviceDriver} contract is used by ui-execution's STEP executor.
 *
 * <p>WDA must be running on the host and reachable at {@code wdaUrl}; the
 * session is created lazily. This keeps Android/Sonic drivers untouched.</p>
 */
public class IosWdaDeviceDriver implements DeviceDriver {

    private static final Logger log = LoggerFactory.getLogger(IosWdaDeviceDriver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SCREEN_WIDTH = 1080;
    private static final int SCREEN_HEIGHT = 1920;

    private final DeviceDescriptor descriptor;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private volatile String sessionId;

    public IosWdaDeviceDriver(DeviceDescriptor descriptor, String wdaUrl) {
        this.descriptor = descriptor;
        String normalized = wdaUrl == null ? "" : wdaUrl.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        this.baseUrl = normalized;
    }

    @Override
    public DeviceDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public byte[] screenshot() {
        try {
            JsonNode response = request("GET", sessionPath("/screenshot"), null);
            String base64 = response.path("value").asText(response.path("value").toString());
            base64 = base64.replace("\n", "").replace("\r", "");
            return Base64.getDecoder().decode(base64);
        } catch (RuntimeException e) {
            log.warn("iOS screenshot failed device={}: {}", descriptor.serial(), e.getMessage());
            return new byte[0];
        }
    }

    @Override
    public UiTree uiTree() {
        try {
            JsonNode response = request("GET", sessionPath("/source"), null);
            String xml = response.path("value").asText("");
            return parseTree(xml);
        } catch (RuntimeException e) {
            log.warn("iOS ui-tree failed device={}: {}", descriptor.serial(), e.getMessage());
            return new UiTree(SCREEN_WIDTH, SCREEN_HEIGHT, new Screen("ios", "iOS", List.of(), List.of()));
        }
    }

    @Override
    public ActionResult action(DeviceAction action) {
        if (action == null || action.getAction() == null) {
            return ActionResult.fail("action is empty", "ios");
        }
        String type = action.getAction().trim().toLowerCase(Locale.ROOT);
        try {
            switch (type) {
                case "click", "tap" -> {
                    int[] point = action.getFrom() != null
                            ? new int[]{action.getFrom().getX(), action.getFrom().getY()}
                            : locate(action.getTarget());
                    if (point == null) return ActionResult.fail("element not found: "
                            + (action.getTarget() == null ? "" : action.getTarget().describe()), "ios");
                    return post("POST", sessionPath("/wda/tap"), Map.of("x", point[0], "y", point[1]), "tapped");
                }
                case "input" -> {
                    int[] point = locate(action.getTarget());
                    if (point == null) return ActionResult.fail("input element not found", "ios");
                    ActionResult tap = post("POST", sessionPath("/wda/tap"),
                            Map.of("x", point[0], "y", point[1]), "focused");
                    if (!tap.ok()) return tap;
                    return post("POST", sessionPath("/wda/keys"),
                            Map.of("value", List.of(action.getText() == null ? "" : action.getText())), "input");
                }
                case "swipe" -> {
                    if (action.getFrom() == null || action.getTo() == null) {
                        return ActionResult.fail("swipe needs from/to", "ios");
                    }
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("fromX", action.getFrom().getX()); body.put("fromY", action.getFrom().getY());
                    body.put("toX", action.getTo().getX()); body.put("toY", action.getTo().getY());
                    body.put("duration", action.getDurationMs() == null ? 300 : action.getDurationMs() / 1000.0);
                    return post("POST", sessionPath("/wda/dragfromtoforduration"), body, "swiped");
                }
                case "back" -> {
                    return post("POST", sessionPath("/wda/back"), Map.of(), "back");
                }
                case "home" -> {
                    return post("POST", sessionPath("/wda/homescreen"), Map.of(), "home");
                }
                case "launch" -> {
                    String app = action.getApp();
                    if (app == null || app.isBlank()) return ActionResult.fail("launch needs app", "ios");
                    return post("POST", sessionPath("/wda/apps/launch"), Map.of("bundleId", app), "launched");
                }
                case "wait" -> {
                    int ms = action.getMs() == null ? 300 : Math.max(0, action.getMs());
                    Thread.sleep(ms);
                    return ActionResult.ok("waited " + ms + "ms", "ios");
                }
                default -> {
                    return ActionResult.fail("unsupported iOS action: " + type, "ios");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResult.fail("interrupted", "ios");
        } catch (RuntimeException e) {
            return ActionResult.fail(e.getMessage(), "ios");
        }
    }

    @Override
    public ActionResult reset() {
        return action(DeviceAction.home());
    }

    private int[] locate(DeviceAction.Target target) {
        if (target == null || target.isBlank()) return null;
        UiTree tree = uiTree();
        for (UiElement element : tree.elements()) {
            if (element == null || element.bounds() == null) continue;
            boolean matched = (target.getId() != null && !target.getId().isBlank()
                    && element.id() != null && element.id().contains(target.getId()))
                    || (target.getText() != null && !target.getText().isBlank()
                    && ((element.text() != null && element.text().contains(target.getText()))
                    || (element.id() != null && element.id().contains(target.getText()))));
            if (matched) return new int[]{element.bounds().centerX(), element.bounds().centerY()};
        }
        return null;
    }

    private UiTree parseTree(String xml) {
        if (xml == null || xml.isBlank()) {
            return new UiTree(SCREEN_WIDTH, SCREEN_HEIGHT, new Screen("ios", "iOS", List.of(), List.of()));
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            List<UiElement> elements = new ArrayList<>();
            Set<String> texts = new LinkedHashSet<>();
            collect(document.getDocumentElement(), elements, texts);
            String app = document.getDocumentElement().getAttribute("name");
            Screen screen = new Screen(app, app, new ArrayList<>(texts), elements);
            return new UiTree(SCREEN_WIDTH, SCREEN_HEIGHT, screen, elements);
        } catch (Exception e) {
            log.debug("iOS source parse failed device={}: {}", descriptor.serial(), e.getMessage());
            return new UiTree(SCREEN_WIDTH, SCREEN_HEIGHT, new Screen("ios", "iOS", List.of(), List.of()));
        }
    }

    private void collect(Element element, List<UiElement> elements, Set<String> texts) {
        if (element == null) return;
        String type = element.getAttribute("type");
        String name = first(element.getAttribute("name"), element.getAttribute("label"),
                element.getAttribute("value"));
        if (name != null && !name.isBlank()) texts.add(name);
        int x = intAttribute(element, "x");
        int y = intAttribute(element, "y");
        int width = intAttribute(element, "width");
        int height = intAttribute(element, "height");
        if (width > 0 && height > 0) {
            String normalized = type == null ? "text" : type.toLowerCase(Locale.ROOT);
            String kind = normalized.contains("button") ? "button"
                    : normalized.contains("textfield") || normalized.contains("textview") ? "editable"
                    : "text";
            elements.add(new UiElement(attribute(element, "name"),
                    name, kind, normalized.contains("button"), kind.equals("editable"),
                    new Bounds(x, y, width, height)));
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) collect(child, elements, texts);
        }
    }

    private String sessionPath(String suffix) {
        return "/session/" + ensureSession() + suffix;
    }

    private synchronized String ensureSession() {
        if (sessionId != null) return sessionId;
        if (baseUrl.isBlank()) throw new IllegalStateException("wdaUrl is empty");
        Map<String, Object> body = Map.of("capabilities", Map.of("alwaysMatch", Map.of()));
        JsonNode response = request("POST", "/session", body);
        String id = response.path("sessionId").asText(null);
        if (id == null) id = response.path("value").path("sessionId").asText(null);
        if (id == null) throw new IllegalStateException("WDA did not return sessionId");
        sessionId = id;
        log.info("iOS WDA session created device={} session={}", descriptor.serial(), id);
        return id;
    }

    private ActionResult post(String method, String path, Object body, String success) {
        try {
            JsonNode response = request(method, path, body);
            JsonNode value = response.path("value");
            if (!value.isMissingNode() && value.has("error") && !value.path("error").asText().isBlank()) {
                return ActionResult.fail(value.path("message").asText(value.path("error").asText()), "ios");
            }
            return ActionResult.ok(success, "ios");
        } catch (RuntimeException e) {
            return ActionResult.fail(e.getMessage(), "ios");
        }
    }

    private JsonNode request(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(
                                MAPPER.writeValueAsString(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("WDA HTTP " + response.statusCode() + ": "
                        + response.body());
            }
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("WDA request failed: " + e.getMessage(), e);
        }
    }

    private static String attribute(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static int intAttribute(Element element, String name) {
        try {
            String value = element.getAttribute(name);
            return value == null || value.isBlank() ? 0 : (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
