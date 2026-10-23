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
}
