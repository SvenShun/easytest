package com.testhub.agent.driver;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe simulated Android app used for offline demos.
 *
 * <p>The app {@code com.testhub.demo} has a small state machine:
 * HOME, LOGIN, LOGIN_ERROR, HOME_LOGGED_IN and SEARCH. Every device instance owns
 * its own state, so two simulated devices never share credentials/screens.</p>
 */
public class SimulatedDeviceDriver implements DeviceDriver {

    public static final String APP_ID = "com.testhub.demo";

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;

    private final DeviceDescriptor descriptor;
    private final ScreenRenderer renderer;

    private State state = State.HOME;
    private String username = "";
    private String password = "";
    private String searchQuery = "";
    private boolean loggedIn;

    public SimulatedDeviceDriver(DeviceDescriptor descriptor, ScreenRenderer renderer) {
        this.descriptor = descriptor;
        this.renderer = renderer;
    }

    /** Default Pixel 7 emulator. */
    public static SimulatedDeviceDriver pixel7(ScreenRenderer renderer) {
        return new SimulatedDeviceDriver(new DeviceDescriptor(
                "emulator-5554", "Pixel 7", "sdk_gphone64", "android",
                List.of("android", "simulated"), true), renderer);
    }

    /** Default Xiaomi 13 simulator. */
    public static SimulatedDeviceDriver xiaomi13(ScreenRenderer renderer) {
        return new SimulatedDeviceDriver(new DeviceDescriptor(
                "simulator-0001", "Xiaomi 13", "2211133C", "android",
                List.of("android", "simulated"), true), renderer);
    }

    @Override
    public DeviceDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public synchronized byte[] screenshot() {
        return renderer.render(uiTree());
    }

    @Override
    public synchronized UiTree uiTree() {
        return new UiTree(WIDTH, HEIGHT, buildScreen());
    }

    @Override
    public synchronized ActionResult action(DeviceAction request) {
        if (request == null || request.getAction() == null || request.getAction().isBlank()) {
            return ActionResult.fail("unsupported action: <empty>", screenSlug());
        }
        String action = request.getAction().trim().toLowerCase();
        return switch (action) {
            case "click", "tap" -> click(request.getTarget());
            case "input", "settext", "type" -> input(request);
            case "swipe", "scroll" -> ActionResult.ok("swiped", screenSlug());
            case "back" -> back();
            case "home" -> {
                state = State.HOME;
                yield ActionResult.ok("pressed home", screenSlug());
            }
            case "launch" -> launch(request.getApp());
            case "wait" -> waitAction(request.getMs() != null ? request.getMs() : request.getDurationMs());
            default -> ActionResult.fail("unsupported action: " + request.getAction(), screenSlug());
        };
    }

    @Override
    public synchronized ActionResult reset() {
        state = State.HOME;
        username = "";
        password = "";
        searchQuery = "";
        loggedIn = false;
        return ActionResult.ok("reset", screenSlug());
    }

    // ------------------------------------------------------------------
    // actions
    // ------------------------------------------------------------------

    private ActionResult click(DeviceAction.Target target) {
        UiElement element = findClickable(target);
        if (element == null) {
            return ActionResult.fail("element not found: " + describe(target), screenSlug());
        }
        switch (element.id()) {
            case "btn-login" -> {
                if (state == State.HOME) {
                    state = State.LOGIN;
                    return ActionResult.ok("clicked 登录", screenSlug());
                }
                if (state == State.LOGIN || state == State.LOGIN_ERROR) {
                    if (username.isBlank() || password.isBlank()) {
                        state = State.LOGIN_ERROR;
                        return ActionResult.ok("clicked 登录", screenSlug());
                    }
                    loggedIn = true;
                    state = State.HOME_LOGGED_IN;
                    return ActionResult.ok("clicked 登录", screenSlug());
                }
                return ActionResult.ok("clicked 登录", screenSlug());
            }
            case "btn-back" -> {
                state = State.HOME;
                return ActionResult.ok("clicked 返回", screenSlug());
            }
            case "btn-search" -> {
                if (state == State.HOME || state == State.HOME_LOGGED_IN) {
                    searchQuery = "";
                    state = State.SEARCH;
                } else if (state == State.SEARCH) {
                    if (searchQuery.isBlank()) {
                        return ActionResult.ok("searched: <empty>", screenSlug());
                    }
                    return ActionResult.ok("searched: " + searchQuery, screenSlug());
                }
                return ActionResult.ok("clicked 搜索", screenSlug());
            }
            case "btn-logout" -> {
                loggedIn = false;
                username = "";
                password = "";
                state = State.HOME;
                return ActionResult.ok("clicked 退出登录", screenSlug());
            }
            default -> {
                return ActionResult.ok("clicked " + element.text(), screenSlug());
            }
        }
    }

    private ActionResult input(DeviceAction request) {
        UiElement element = findEditable(request.getTarget());
        if (element == null) {
            return ActionResult.fail("element not found: " + describe(request.getTarget()), screenSlug());
        }
        String value = request.getText() == null ? "" : request.getText();
        switch (element.id()) {
            case "input-username" -> username = value;
            case "input-password" -> password = value;
            case "input-search" -> searchQuery = value;
            default -> {
                return ActionResult.fail("element not editable: " + element.text(), screenSlug());
            }
        }
        return ActionResult.ok("input " + element.text(), screenSlug());
    }

    private ActionResult back() {
        switch (state) {
            case LOGIN, LOGIN_ERROR -> {
                state = State.HOME;
                return ActionResult.ok("pressed back", screenSlug());
            }
            case SEARCH -> {
                state = loggedIn ? State.HOME_LOGGED_IN : State.HOME;
                return ActionResult.ok("pressed back", screenSlug());
            }
            case HOME_LOGGED_IN -> {
                state = State.HOME;
                return ActionResult.ok("pressed back", screenSlug());
            }
            default -> {
                return ActionResult.ok("pressed back", screenSlug());
            }
        }
    }

    private ActionResult launch(String app) {
        String targetApp = (app == null || app.isBlank()) ? APP_ID : app.trim();
        if (!APP_ID.equals(targetApp)) {
            return ActionResult.fail("app not found: " + targetApp, screenSlug());
        }
        state = State.HOME;
        username = "";
        password = "";
        searchQuery = "";
        loggedIn = false;
        return ActionResult.ok("launched " + APP_ID, screenSlug());
    }

    private ActionResult waitAction(Integer requestedMs) {
        int ms = requestedMs == null ? 200 : Math.max(0, Math.min(requestedMs, 10_000));
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResult.fail("wait interrupted", screenSlug());
        }
        return ActionResult.ok("waited " + ms + "ms", screenSlug());
    }

    // ------------------------------------------------------------------
    // element matching
    // ------------------------------------------------------------------

    private UiElement findClickable(DeviceAction.Target target) {
        List<UiElement> elements = buildScreen().elements();
        UiElement byTarget = match(elements, target, false);
        if (byTarget != null) {
            return byTarget;
        }
        if (target != null && target.getIndex() != null) {
            List<UiElement> clickable = elements.stream().filter(UiElement::clickable).toList();
            int index = target.getIndex();
            if (index >= 0 && index < clickable.size()) {
                return clickable.get(index);
            }
        }
        return null;
    }

    private UiElement findEditable(DeviceAction.Target target) {
        List<UiElement> elements = buildScreen().elements();
        UiElement byTarget = match(elements, target, true);
        if (byTarget != null) {
            return byTarget;
        }
        List<UiElement> editable = elements.stream().filter(UiElement::editable).toList();
        if (target != null && target.getIndex() != null) {
            int index = target.getIndex();
            if (index >= 0 && index < editable.size()) {
                return editable.get(index);
            }
            return null;
        }
        // Some models emit text-less targets; fall back to the first editable field.
        if (editable.size() == 1) {
            return editable.get(0);
        }
        return null;
    }

    private UiElement match(List<UiElement> elements, DeviceAction.Target target, boolean editableOnly) {
        if (target == null || target.isBlank()) {
            return null;
        }
        if (target.getId() != null && !target.getId().isBlank()) {
            for (UiElement element : elements) {
                if (target.getId().equals(element.id()) && (!editableOnly || element.editable())) {
                    return element;
                }
            }
        }
        if (target.getText() != null && !target.getText().isBlank()) {
            for (UiElement element : elements) {
                if (target.getText().equals(element.text()) && (!editableOnly || element.editable())) {
                    return element;
                }
            }
        }
        return null;
    }

    private String describe(DeviceAction.Target target) {
        return target == null ? "<empty>" : target.describe();
    }

    // ------------------------------------------------------------------
    // screen model
    // ------------------------------------------------------------------

    private Screen buildScreen() {
        List<String> texts;
        List<UiElement> elements = new ArrayList<>();
        switch (state) {
            case LOGIN -> {
                texts = List.of();
                elements.add(input("input-username", "用户名", 90, 430, 540, 88));
                elements.add(input("input-password", "密码", 90, 550, 540, 88));
                elements.add(button("btn-login", "登录", 90, 700, 540, 96));
                elements.add(button("btn-back", "返回", 90, 830, 540, 96));
                return new Screen(APP_ID, "登录", texts, elements);
            }
            case LOGIN_ERROR -> {
                texts = List.of("请先输入用户名和密码");
                elements.add(input("input-username", "用户名", 90, 430, 540, 88));
                elements.add(input("input-password", "密码", 90, 550, 540, 88));
                elements.add(button("btn-login", "登录", 90, 700, 540, 96));
                elements.add(button("btn-back", "返回", 90, 830, 540, 96));
                return new Screen(APP_ID, "登录", texts, elements);
            }
            case HOME_LOGGED_IN -> {
                texts = List.of("登录成功", "欢迎, tester");
                elements.add(button("btn-search", "搜索", 120, 650, 480, 96));
                elements.add(button("btn-logout", "退出登录", 120, 780, 480, 96));
                return new Screen(APP_ID, "首页", texts, elements);
            }
            case SEARCH -> {
                texts = searchQuery.isBlank()
                        ? List.of("请输入搜索关键词")
                        : List.of("搜索：" + searchQuery, "共 3 件商品");
                elements.add(input("input-search", "搜索商品", 90, 420, 540, 88));
                elements.add(button("btn-search", "搜索", 90, 570, 540, 96));
                return new Screen(APP_ID, "搜索", texts, elements);
            }
            case HOME -> {
                texts = List.of("欢迎使用 TestHub 演示应用");
                elements.add(button("btn-login", "登录", 120, 560, 480, 96));
                elements.add(button("btn-search", "搜索", 120, 690, 480, 96));
                return new Screen(APP_ID, "TestHub Demo", texts, elements);
            }
            default -> {
                texts = List.of();
                return new Screen(APP_ID, "TestHub Demo", texts, elements);
            }
        }
    }

    private static UiElement button(String id, String text, int x, int y, int width, int height) {
        return new UiElement(id, text, "button", true, false, new Bounds(x, y, width, height));
    }

    private static UiElement input(String id, String text, int x, int y, int width, int height) {
        return new UiElement(id, text, "editable", true, true, new Bounds(x, y, width, height));
    }

    private String screenSlug() {
        return switch (state) {
            case HOME -> "home";
            case LOGIN -> "login";
            case LOGIN_ERROR -> "login_error";
            case HOME_LOGGED_IN -> "home_logged_in";
            case SEARCH -> "search";
        };
    }

    /** App state machine states. */
    public enum State {
        HOME, LOGIN, LOGIN_ERROR, HOME_LOGGED_IN, SEARCH
    }
}
