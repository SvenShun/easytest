package com.easytest.agent.driver;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Headless web driver backed by HtmlUnit. It exposes the same DeviceDriver
 * contract as Android/iOS, so web cases reuse UI STEP execution, queues,
 * reports and artifacts. Swap for Playwright/Selenium later without changing
 * the platform contract.
 */
public class WebDeviceDriver implements DeviceDriver {

    private static final Logger log = LoggerFactory.getLogger(WebDeviceDriver.class);
    private final DeviceDescriptor descriptor;
    private final String startUrl;
    private final ScreenRenderer renderer;
    private WebClient client;
    private HtmlPage page;

    public WebDeviceDriver(DeviceDescriptor descriptor, String startUrl, ScreenRenderer renderer) {
        this.descriptor = descriptor;
        this.startUrl = startUrl;
        this.renderer = renderer;
        reset();
    }

    @Override
    public DeviceDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public synchronized byte[] screenshot() {
        try {
            return renderer.render(uiTree());
        } catch (RuntimeException e) {
            log.debug("web screenshot failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    @Override
    public synchronized UiTree uiTree() {
        if (page == null) return emptyTree();
        try {
            List<UiElement> elements = new ArrayList<>();
            Set<String> texts = new LinkedHashSet<>();
            int count = 0;
            for (DomNode node : page.getDocumentElement().querySelectorAll("*")) {
                if (count++ > 300) break;
                if (!(node instanceof DomElement element)) continue;
                String text = textOf(element);
                if (text != null && !text.isBlank()) texts.add(text);
                String tag = element.getTagName().toLowerCase(Locale.ROOT);
                String type = tag.equals("button") || "button".equalsIgnoreCase(element.getAttribute("role"))
                        ? "button" : tag.equals("input") || tag.equals("textarea") ? "editable" : "text";
                elements.add(new UiElement(element.getId(), text, type,
                        tag.equals("button") || tag.equals("a") || "button".equalsIgnoreCase(element.getAttribute("role")),
                        type.equals("editable"), new Bounds(0, 0, 1, 1)));
            }
            return new UiTree(1080, 1920, new Screen("web", page.getTitleText(), new ArrayList<>(texts), elements),
                    elements);
        } catch (RuntimeException e) {
            return emptyTree();
        }
    }

    @Override
    public synchronized ActionResult action(DeviceAction action) {
        if (action == null || action.getAction() == null) return ActionResult.fail("action is empty", "web");
        try {
            String type = action.getAction().toLowerCase(Locale.ROOT);
            switch (type) {
                case "click", "tap" -> {
                    HtmlElement element = find(action.getTarget());
                    if (element == null) return ActionResult.fail("web element not found: "
                            + (action.getTarget() == null ? "" : action.getTarget().describe()), "web");
                    Page next = element.click();
                    if (next instanceof HtmlPage nextPage) page = nextPage;
                    return ActionResult.ok("clicked web element", "web");
                }
                case "input" -> {
                    HtmlElement element = find(action.getTarget());
                    if (!(element instanceof HtmlInput input)) {
                        return ActionResult.fail("web input not found", "web");
                    }
                    input.setValueAttribute(action.getText() == null ? "" : action.getText());
                    return ActionResult.ok("input web element", "web");
                }
                case "launch" -> {
                    String url = action.getApp() == null || action.getApp().isBlank() ? startUrl : action.getApp();
                    page = client.getPage(url);
                    return ActionResult.ok("loaded " + url, "web");
                }
                case "back" -> {
                    client.getCurrentWindow().getHistory().back();
                    if (client.getCurrentWindow().getEnclosedPage() instanceof HtmlPage backPage) page = backPage;
                    return ActionResult.ok("web back", "web");
                }
                case "home" -> {
                    page = client.getPage(startUrl);
                    return ActionResult.ok("web home", "web");
                }
                case "wait" -> {
                    int ms = action.getMs() == null ? 300 : Math.max(0, action.getMs());
                    Thread.sleep(ms);
                    return ActionResult.ok("waited " + ms + "ms", "web");
                }
                default -> {
                    return ActionResult.fail("unsupported web action: " + type, "web");
                }
            }
        } catch (Exception e) {
            return ActionResult.fail("web action failed: " + e.getMessage(), "web");
        }
    }

    @Override
    public synchronized ActionResult reset() {
        try {
            client = new WebClient(BrowserVersion.CHROME);
            client.getOptions().setJavaScriptEnabled(true);
            client.getOptions().setCssEnabled(false);
            client.getOptions().setThrowExceptionOnScriptError(false);
            client.getOptions().setThrowExceptionOnFailingStatusCode(false);
            page = client.getPage(startUrl);
            return ActionResult.ok("web reset -> " + startUrl, "web");
        } catch (Exception e) {
            page = null;
            return ActionResult.fail("web reset failed: " + e.getMessage(), "web");
        }
    }

    private HtmlElement find(DeviceAction.Target target) {
        if (target == null || page == null) return null;
        if (target.getId() != null && !target.getId().isBlank()) {
            HtmlElement byId = page.getHtmlElementById(target.getId());
            if (byId != null) return byId;
        }
        String needle = target.getText() != null && !target.getText().isBlank()
                ? target.getText() : target.getId();
        if (needle == null) return null;
        for (DomNode node : page.getDocumentElement().querySelectorAll("*")) {
            if (!(node instanceof HtmlElement element)) continue;
            if (textOf(element).contains(needle)) return element;
        }
        return null;
    }

    private String textOf(DomElement element) {
        String text = element.asNormalizedText();
        if (text == null || text.isBlank()) text = element.getAttribute("value");
        if (text == null || text.isBlank()) text = element.getAttribute("placeholder");
        return text == null ? "" : text.trim();
    }

    private UiTree emptyTree() {
        return new UiTree(1080, 1920, new Screen("web", "Web", List.of(), List.of()));
    }
}
