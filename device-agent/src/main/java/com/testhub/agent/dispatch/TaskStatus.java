package com.testhub.agent.dispatch;

import java.util.Collection;
import java.util.Set;

/** Task state constants used by the agent. */
public final class TaskStatus {

    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String CANCELLED = "CANCELLED";

    private static final Set<String> TERMINAL = Set.of(SUCCESS, FAILED, TIMEOUT, CANCELLED);
    private static final Set<String> ALL = Set.of(RUNNING, SUCCESS, FAILED, TIMEOUT, CANCELLED);

    private TaskStatus() {
    }

    public static boolean isTerminal(String status) {
        return status != null && TERMINAL.contains(status);
    }

    public static Set<String> terminalStates() {
        return TERMINAL;
    }

    public static Collection<String> all() {
        return ALL;
    }
}
