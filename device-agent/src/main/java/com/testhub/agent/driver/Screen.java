package com.testhub.agent.driver;

import java.util.List;

/** Logical screen reported by a device driver. */
public record Screen(
        String app,
        String title,
        List<String> texts,
        List<UiElement> elements) {
}
