package com.testhub.agent.driver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.testhub.agent.config.AgentProperties;

/**
 * Owns the device drivers available on this agent. In simulated mode two fake
 * Android devices are always available (offline demo); in adb mode real devices
 * are discovered; auto mode prefers real devices and falls back to simulated.
 */
@Component
public class DeviceManager {

    private static final Logger log = LoggerFactory.getLogger(DeviceManager.class);

    private final Map<String, DeviceDriver> devices = new LinkedHashMap<>();
    private final String mode;

    public DeviceManager(AgentProperties properties, ScreenRenderer renderer) {
        String configuredMode = properties.getMode();
        String mode = configuredMode == null ? "simulated" : configuredMode.trim().toLowerCase(Locale.ROOT);
        if (!"adb".equals(mode) && !"auto".equals(mode)) {
            mode = "simulated";
        }
        this.mode = mode;

        if ("adb".equals(mode) || "auto".equals(mode)) {
            List<DeviceDescriptor> discovered = AdbDeviceDriver.discover("adb");
            for (DeviceDescriptor descriptor : discovered) {
                devices.put(descriptor.serial(), new AdbDeviceDriver(descriptor, "adb"));
            }
            log.info("adb mode discovered {} device(s)", discovered.size());
        }

        if ("simulated".equals(mode) || ("auto".equals(mode) && devices.isEmpty())) {
            SimulatedDeviceDriver pixel7 = SimulatedDeviceDriver.pixel7(renderer);
            SimulatedDeviceDriver xiaomi13 = SimulatedDeviceDriver.xiaomi13(renderer);
            devices.put(pixel7.serial(), pixel7);
            devices.put(xiaomi13.serial(), xiaomi13);
            log.info("simulated mode enabled with devices {} and {}", pixel7.serial(), xiaomi13.serial());
        }

        if (devices.isEmpty()) {
            log.warn("agent started with no devices (mode={})", mode);
        }
    }

    /** Register an extra driver (used by tests). */
    public void register(DeviceDriver driver) {
        devices.put(driver.serial(), driver);
    }

    public List<DeviceDescriptor> descriptors() {
        List<DeviceDescriptor> result = new ArrayList<>(devices.size());
        for (DeviceDriver driver : devices.values()) {
            result.add(driver.descriptor());
        }
        return result;
    }

    public List<String> serials() {
        return new ArrayList<>(devices.keySet());
    }

    public Optional<DeviceDriver> find(String serial) {
        if (serial == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(devices.get(serial));
    }

    public boolean exists(String serial) {
        return find(serial).isPresent();
    }

    public String mode() {
        return mode;
    }
}
