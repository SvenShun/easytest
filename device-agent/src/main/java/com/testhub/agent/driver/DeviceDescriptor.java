package com.testhub.agent.driver;

import java.util.List;

/** Device identity advertised to ui-execution on register/heartbeat. */
public record DeviceDescriptor(
        String serial,
        String name,
        String model,
        String platform,
        List<String> tags,
        boolean online) {
}
