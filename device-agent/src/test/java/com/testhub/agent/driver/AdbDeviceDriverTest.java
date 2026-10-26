package com.testhub.agent.driver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdbDeviceDriverTest {

    @Test
    void discoversScreenshotsTreeClicksAndInstalls() throws Exception {
        Path dir = Files.createTempDirectory("fake-adb");
        Path adb = FakeAdb.create(dir);
        List<DeviceDescriptor> devices = AdbDeviceDriver.discover(adb.toString());
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).serial()).isEqualTo("fake-1");

        AdbDeviceDriver driver = new AdbDeviceDriver(devices.get(0), adb.toString());
        assertThat(driver.screenshot()).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);

        UiTree tree = driver.uiTree();
        assertThat(tree.width()).isEqualTo(200);
        assertThat(tree.height()).isEqualTo(400);
        assertThat(tree.screen().elements()).extracting(UiElement::text).contains("登录");

        ActionResult clicked = driver.action(DeviceAction.click("登录"));
        assertThat(clicked.ok()).isTrue();
        assertThat(Files.readString(FakeAdb.log(dir))).contains("shell input tap 60 50");

        Files.createFile(dir.resolve("blocked"));
        ActionResult blocked = driver.action(DeviceAction.back());
        assertThat(blocked.ok()).isFalse();
        assertThat(blocked.message()).contains("USB");

        ActionResult installed = driver.installApk(new byte[]{1, 2, 3}, true);
        assertThat(installed.ok()).isTrue();
        assertThat(Files.readString(FakeAdb.log(dir))).contains("install -r");
    }
}
