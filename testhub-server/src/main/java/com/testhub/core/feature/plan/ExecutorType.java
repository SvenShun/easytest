package com.testhub.core.feature.plan;

import com.testhub.common.web.ApiException;

import java.util.Locale;

/** Execution engines selectable per plan case. */
public enum ExecutorType {
    SCRIPT,
    AI_RUNTIME;

    /** Parses an optional override; blank means "no override". */
    public static ExecutorType parseOverride(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("executorTypeOverride 仅支持 SCRIPT / AI_RUNTIME");
        }
    }

    public static ExecutorType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
