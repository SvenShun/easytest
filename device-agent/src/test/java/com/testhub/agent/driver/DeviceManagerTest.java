package com.testhub.agent.driver;

import com.testhub.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceManagerTest {

    @Test
    void refreshesAdbDevicesForHotplug() throws Exception {
        Path dir = Files.createTempDirectory("fake-adb-manager");
        Path adb = FakeAdb.create(dir);
        AgentProperties properties = new AgentProperties();
        properties.setMode("adb");
        properties.setAdb(adb.toString());
        DeviceManager manager = new DeviceManager(properties, new ScreenRenderer());

        assertThat(manager.mode()).isEqualTo("adb");
        assertThat(manager.serials()).containsExactly("fake-1");

        Files.writeString(FakeAdb.devices(dir), """
                List of devices attached
                fake-1 device product:test model:Pixel_7 device:test
                fake-2 device product:test model:Pixel_8 device:test
                """);
        manager.refresh();
        assertThat(manager.serials()).containsExactlyInAnyOrder("fake-1", "fake-2");

        Files.writeString(FakeAdb.devices(dir), "List of devices attached\n");
        manager.refresh();
        assertThat(manager.serials()).isEmpty();
    }
}
