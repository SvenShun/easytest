package com.testhub.agent.driver;

/** Abstraction over a simulated or ADB-backed device. */
public interface DeviceDriver {

    DeviceDescriptor descriptor();

    default String serial() {
        return descriptor().serial();
    }

    /** PNG bytes of the current screen. */
    byte[] screenshot();

    /** Current UI hierarchy. */
    UiTree uiTree();

    /** Execute one action. Action failures use {@code ok=false} and must not throw. */
    ActionResult action(DeviceAction action);

    /** Reset the device to its initial state. */
    ActionResult reset();

    /** Execute a shell command on the device. */
    default DeviceShellResult shell(String command, int timeoutSec) {
        return new DeviceShellResult(1, "", "shell not supported by " + descriptor().platform());
    }

    /** List installed third-party packages. */
    default java.util.List<String> apps() {
        return java.util.List.of();
    }

    default ActionResult uninstall(String packageName) {
        return ActionResult.fail("uninstall not supported by " + descriptor().platform(), "driver");
    }

    /** Lightweight performance snapshot (cpu/mem/battery). */
    default java.util.Map<String, Object> performance() {
        return java.util.Map.of();
    }

    /** Install an APK; drivers without install support return ok=false. */
    default ActionResult installApk(byte[] apk, boolean replace) {
        return ActionResult.fail("install not supported by " + descriptor().platform(), "driver");
    }
}
