package com.testhub.agent.driver;

/** Result of an adb shell command. */
public record DeviceShellResult(int exitCode, String stdout, String stderr) {
}
