package com.testhub.uiexec.domain;

/** Task state machine states (docs/API-CONTRACT.md §6). */
public enum TaskStatus {
    PENDING,
    DISPATCHED,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT || this == CANCELLED;
    }

    public boolean isActive() {
        return this == PENDING || this == DISPATCHED || this == RUNNING;
    }
}
