package com.testhub.agent.driver;

import java.util.List;

/**
 * UI hierarchy snapshot returned by {@code GET /device/{serial}/ui-tree}.
 *
 * <p>Contract shape: {@code {width,height,screen:{app,title,texts,elements}}}.
 * The top-level {@code elements} list is a compatibility alias for clients
 * (notably the Python AI Runtime) that read elements from the root object.</p>
 */
public record UiTree(int width, int height, Screen screen, List<UiElement> elements) {

    public UiTree(int width, int height, Screen screen) {
        this(width, height, screen,
                screen == null || screen.elements() == null ? List.of() : screen.elements());
    }
}
