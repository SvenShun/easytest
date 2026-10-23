package com.testhub.agent.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class SimulatedDeviceDriverTest {

    private final ScreenRenderer renderer = new ScreenRenderer();
    private final SimulatedDeviceDriver driver = SimulatedDeviceDriver.pixel7(renderer);

    @Test
    void fullLoginFlow() {
        UiTree home = driver.uiTree();
        assertEquals(720, home.width());
        assertEquals(1280, home.height());
        assertEquals("TestHub Demo", home.screen().title());
        assertTrue(home.screen().texts().contains("欢迎使用 TestHub 演示应用"));
        assertTrue(hasElement(home, "btn-login"));
        assertTrue(hasElement(home, "btn-search"));

        ActionResult openLogin = driver.action(DeviceAction.click("登录"));
        assertTrue(openLogin.ok(), openLogin.message());
        assertEquals("login", openLogin.screen());
        assertEquals("登录", driver.uiTree().screen().title());

        // Clicking login with empty credentials shows the validation error.
        ActionResult emptyLogin = driver.action(DeviceAction.click("登录"));
        assertTrue(emptyLogin.ok());
        assertEquals("login_error", emptyLogin.screen());
        assertTrue(driver.uiTree().screen().texts().contains("请先输入用户名和密码"));

        assertTrue(driver.action(DeviceAction.input("用户名", "tester")).ok());
        assertTrue(driver.action(DeviceAction.input("密码", "secret")).ok());

        ActionResult submit = driver.action(DeviceAction.click("登录"));
        assertTrue(submit.ok(), submit.message());
        assertEquals("home_logged_in", submit.screen());

        UiTree loggedIn = driver.uiTree();
        assertEquals("首页", loggedIn.screen().title());
        assertTrue(loggedIn.screen().texts().contains("登录成功"));
        assertTrue(loggedIn.screen().texts().contains("欢迎, tester"));
        assertTrue(hasElement(loggedIn, "btn-logout"));
        assertFalse(hasElement(loggedIn, "btn-login"));

        // Search from the logged-in home, type a query and search.
        ActionResult search = driver.action(DeviceAction.click("搜索"));
        assertEquals("search", search.screen());
        assertTrue(driver.action(DeviceAction.input("搜索商品", "手机")).ok());
        driver.action(DeviceAction.click("搜索"));
        assertTrue(driver.uiTree().screen().texts().contains("搜索：手机"));

        // Logout returns to the initial home screen.
        driver.action(DeviceAction.back());
        ActionResult logout = driver.action(DeviceAction.click("退出登录"));
        assertTrue(logout.ok(), logout.message());
        assertEquals("home", logout.screen());
    }

    @Test
    void inputByEditableIndexAndBack() {
        assertTrue(driver.action(DeviceAction.click("登录")).ok());

        DeviceAction byIndex = new DeviceAction("input");
        byIndex.setTarget(new DeviceAction.Target(null, null, 1));
        byIndex.setText("secret");
        ActionResult result = driver.action(byIndex);
        assertTrue(result.ok(), result.message());

        assertTrue(driver.action(DeviceAction.input("用户名", "tester")).ok());
        assertEquals("home_logged_in", driver.action(DeviceAction.click("登录")).screen());

        ActionResult back = driver.action(DeviceAction.back());
        assertTrue(back.ok());
        assertEquals("home", back.screen());
    }

    @Test
    void missingElementReturnsOkFalse() {
        ActionResult result = driver.action(DeviceAction.click("不存在的按钮"));
        assertFalse(result.ok());
        assertTrue(result.message().contains("element not found"));
        assertTrue(result.message().contains("不存在的按钮"));
        assertEquals("home", result.screen());

        ActionResult input = driver.action(DeviceAction.input("不存在输入框", "x"));
        assertFalse(input.ok());
        assertTrue(input.message().contains("element not found"));
    }

    @Test
    void resetReturnsToHome() {
        driver.action(DeviceAction.click("登录"));
        driver.action(DeviceAction.input("用户名", "tester"));
        driver.action(DeviceAction.input("密码", "secret"));
        assertEquals("home_logged_in", driver.action(DeviceAction.click("登录")).screen());

        ActionResult reset = driver.reset();
        assertTrue(reset.ok());
        assertEquals("home", reset.screen());

        UiTree tree = driver.uiTree();
        assertEquals("TestHub Demo", tree.screen().title());
        assertTrue(tree.screen().texts().contains("欢迎使用 TestHub 演示应用"));
        assertTrue(hasElement(tree, "btn-login"));
    }

    @Test
    void screenshotIsPng() {
        byte[] png = driver.screenshot();
        assertNotNull(png);
        assertTrue(png.length > 1000, "screenshot should contain pixels");
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);
    }

    @Test
    void devicesHaveIndependentState() {
        SimulatedDeviceDriver second = SimulatedDeviceDriver.xiaomi13(renderer);
        assertEquals("simulator-0001", second.serial());
        assertEquals("Xiaomi 13", second.descriptor().name());

        driver.action(DeviceAction.click("登录"));
        driver.action(DeviceAction.input("用户名", "tester"));
        driver.action(DeviceAction.input("密码", "secret"));
        driver.action(DeviceAction.click("登录"));

        assertTrue(driver.uiTree().screen().texts().contains("登录成功"));
        assertEquals("TestHub Demo", second.uiTree().screen().title());
        assertFalse(second.uiTree().screen().texts().contains("登录成功"));
    }

    @Test
    void acceptsRuntimeStyleTypeAliasAndExposesTopLevelElements() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DeviceAction action = mapper.readValue(
                "{\"type\":\"click\",\"target\":{\"text\":\"登录\"}}", DeviceAction.class);
        assertEquals("click", action.getAction());
        assertEquals("login", driver.action(action).screen());

        JsonNode tree = mapper.readTree(mapper.writeValueAsString(driver.uiTree()));
        assertEquals(720, tree.path("width").asInt());
        assertEquals(1280, tree.path("height").asInt());
        assertTrue(tree.path("elements").isArray());
        assertTrue(tree.path("screen").path("elements").isArray());
        assertEquals(tree.path("screen").path("elements").size(), tree.path("elements").size());
    }

    private static boolean hasElement(UiTree tree, String id) {
        List<UiElement> elements = tree.screen().elements();
        return elements != null && elements.stream().anyMatch(element -> id.equals(element.id()));
    }
}
