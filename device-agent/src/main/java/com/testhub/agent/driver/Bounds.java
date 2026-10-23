package com.testhub.agent.driver;

/** Element bounds in device pixels, origin at the top-left of the screen. */
public record Bounds(int x, int y, int width, int height) {

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }
}
