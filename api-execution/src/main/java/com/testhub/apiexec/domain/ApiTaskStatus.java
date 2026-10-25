package com.testhub.apiexec.domain;

/** Lifecycle of an API execution task. */
public enum ApiTaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT || this == CANCELLED;
    }

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
