package com.testhub.core.feature.casemgmt;

import com.testhub.common.web.ApiException;

import java.util.Locale;

/**
 * How a UI case is executed.
 *
 * <ul>
 *   <li>{@link #AI} — the existing BDD + AI runtime flow (default).</li>
 *   <li>{@link #STEPS} — declarative {@code case_step} rows executed deterministically on the device.</li>
 * </ul>
 */
public enum ExecutionMode {
    AI,
    STEPS;

    /** Lenient parser used by the case create/update API; blank means "not provided". */
    public static ExecutionMode parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("executionMode 仅支持 AI / STEPS");
        }
    }

    public static ExecutionMode parseOrDefault(String value, ExecutionMode fallback) {
        ExecutionMode parsed = parse(value);
        return parsed == null ? fallback : parsed;
    }
}
