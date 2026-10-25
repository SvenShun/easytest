package com.testhub.agent.driver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.testhub.agent.config.AgentProperties;

/**
 * Owns the device drivers available on this agent. In simulated mode two fake
 * Android devices are always available (offline demo); in adb mode real devices
 * are discovered and refreshed on a schedule (hotplug); auto mode prefers real
 * devices and falls back to simulated.
 */
@Component
public class DeviceManager {

    private static final Logger log = LoggerFactory.getLogger(DeviceManager.class);

    private final Map<String, DeviceDriver> devices = new ConcurrentHashMap<>();
    private final Set<String> adbSerials = ConcurrentHashMap.newKeySet();
    private final String mode;
    private final String adb;
    private final ScreenRenderer renderer;

    public DeviceManager(AgentProperties properties, ScreenRenderer renderer) {
        this.renderer = renderer;
        String configuredMode = properties.getMode();
        String normalised = configuredMode == null ? "simulated"
                : configuredMode.trim().toLowerCase(Locale.ROOT);
        if (!"adb".equals(normalised) && !"auto".equals(normalised) && !"sonic".equals(normalised)) {
            normalised = "simulated";
        }
        this.mode = normalised;
        this.adb = properties.getAdb() == null || properties.getAdb().isBlank()
                ? "adb" : properties.getAdb().trim();

        if ("adb".equals(mode) || "auto".equals(mode)) {
            refreshAdbDevices();
        }

        if ("sonic".equals(mode)) {
            for (AgentProperties.SonicDevice configured : properties.getSonic().getDevices()) {
                if (configured.getSerial() == null || configured.getSerial().isBlank()
                        || configured.getUiaUrl() == null || configured.getUiaUrl().isBlank()) {
                    log.warn("skipping sonic device with missing serial/uiaUrl");
                    continue;
                }
                DeviceDescriptor descriptor = new DeviceDescriptor(configured.getSerial(),
                        configured.getName() == null ? configured.getSerial() : configured.getName(),
                        configured.getModel() == null ? "android" : configured.getModel(),
                        "android", configured.getTags(), true);
                try {
                    devices.put(descriptor.serial(),
                            new SonicAndroidDeviceDriver(descriptor, configured.getUiaUrl(),
                                    properties.getSonic().getAdb()));
                } catch (RuntimeException e) {
                    log.warn("sonic device {} unavailable: {}", descriptor.serial(), e.getMessage());
                }
            }
            log.info("sonic mode configured {} device(s)", devices.size());
        }

        if ("simulated".equals(mode) || ("auto".equals(mode) && devices.isEmpty())) {
            addSimulatedDevices();
        }

        if (devices.isEmpty()) {
            log.warn("agent started with no devices (mode={})", mode);
        }
    }

    /** Re-discovers adb devices; safe to call from the refresh endpoint/scheduler. */
    public synchronized void refresh() {
        if (!"adb".equals(mode) && !"auto".equals(mode)) {
            return;
        }
        refreshAdbDevices();
        if ("auto".equals(mode) && devices.isEmpty()) {
            addSimulatedDevices();
        }
    }

    @Scheduled(fixedDelayString = "${testhub.agent.device-refresh-ms:15000}")
    public void scheduledRefresh() {
        refresh();
    }

    private void refreshAdbDevices() {
        List<DeviceDescriptor> discovered = AdbDeviceDriver.discover(adb);
        Set<String> current = ConcurrentHashMap.newKeySet();
        for (DeviceDescriptor descriptor : discovered) {
            current.add(descriptor.serial());
            devices.computeIfAbsent(descriptor.serial(),
                    serial -> new AdbDeviceDriver(descriptor, adb));
        }
        for (String serial : new ArrayList<>(adbSerials)) {
            if (!current.contains(serial)) {
                devices.remove(serial);
                log.info("adb device removed: {}", serial);
            }
        }
        adbSerials.clear();
        adbSerials.addAll(current);
        log.info("adb mode discovered {} device(s) via {}", discovered.size(), adb);
    }

    private void addSimulatedDevices() {
        SimulatedDeviceDriver pixel7 = SimulatedDeviceDriver.pixel7(renderer);
        SimulatedDeviceDriver xiaomi13 = SimulatedDeviceDriver.xiaomi13(renderer);
        devices.putIfAbsent(pixel7.serial(), pixel7);
        devices.putIfAbsent(xiaomi13.serial(), xiaomi13);
        log.info("simulated mode enabled with devices {} and {}", pixel7.serial(), xiaomi13.serial());
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

    public int size() {
        return devices.size();
    }
}
