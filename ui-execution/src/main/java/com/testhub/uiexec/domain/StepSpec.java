package com.testhub.uiexec.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Locale;

/**
 * Declarative step carried by a {@code STEP} task. Mirrors {@code case_step} on the TestHub side.
 *
 * <p>{@code seq} keeps the original ordering (falling back to the list position), and
 * {@code timeoutMs = 0/null} means "use the task's {@code stepTimeoutSec}".</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StepSpec(Integer seq, String stepType, String selectorType, String selector, String value,
                       String assertValue, Integer timeoutMs, Boolean enabled) {

    public static final List<String> SUPPORTED_TYPES = List.of(
            "CLICK", "INPUT", "TAP", "SWIPE", "BACK", "HOME", "WAIT", "LAUNCH",
            "ASSERT_TEXT", "ASSERT_EXISTS", "SCREENSHOT");

    public int seqOr(int fallback) {
        return seq == null || seq <= 0 ? fallback : seq;
    }

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }

    public String stepTypeName() {
        return normalize(stepType);
    }

    public String selectorTypeName() {
        return normalize(selectorType);
    }

    public long timeoutMsOr(long fallbackMs) {
        return timeoutMs == null || timeoutMs <= 0 ? fallbackMs : timeoutMs;
    }

    public static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
