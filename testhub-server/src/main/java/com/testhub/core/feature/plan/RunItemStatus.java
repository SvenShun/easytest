package com.testhub.core.feature.plan;

/** Item states use the task state names plus SKIPPED. */
public enum RunItemStatus {
    PENDING,
    DISPATCHED,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED,
    SKIPPED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT || this == CANCELLED || this == SKIPPED;
    }

    public boolean isFailure() {
        return this == FAILED || this == TIMEOUT || this == CANCELLED;
    }

    public boolean isNonTerminal() {
        return !isTerminal();
    }

    public static RunItemStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
