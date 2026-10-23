package com.testhub.agent.driver;

/**
 * One element of the UI tree.
 *
 * @param type one of {@code button}, {@code editable}, {@code text}
 */
public record UiElement(
        String id,
        String text,
        String type,
        boolean clickable,
        boolean editable,
        Bounds bounds) {
}
