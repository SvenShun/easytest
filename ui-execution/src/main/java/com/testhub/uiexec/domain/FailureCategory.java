package com.testhub.uiexec.domain;

/** Failure classification (docs/V1-SCOPE.md §6). */
public enum FailureCategory {
    MODEL_DECISION_FAILED,
    ACTION_FAILED,
    ASSERTION_FAILED,
    ELEMENT_NOT_FOUND,
    TIMEOUT,
    MANUAL_INTERRUPT,
    EXECUTOR_UNAVAILABLE,
    SKILLS_UNAVAILABLE,
    SCRIPT_FAILED;

    /** Lenient parser used for agent-reported values; returns {@code null} when unknown. */
    public static FailureCategory parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return FailureCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
