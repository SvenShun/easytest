package com.testhub.agent.driver;

/** Result of a device action, matching contract §3. */
public record ActionResult(boolean ok, String message, String screen) {

    public static ActionResult ok(String message, String screen) {
        return new ActionResult(true, message, screen);
    }

    public static ActionResult fail(String message, String screen) {
        return new ActionResult(false, message, screen);
    }
}
