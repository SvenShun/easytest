package com.testhub.core.feature.casemgmt;

import com.testhub.common.web.ApiException;

import java.util.Locale;

/** Declarative, deterministic UI step kinds supported by ui-execution's STEP executor. */
public enum StepType {
    CLICK,
    INPUT,
    TAP,
    SWIPE,
    BACK,
    HOME,
    WAIT,
    LAUNCH,
    ASSERT_TEXT,
    ASSERT_EXISTS,
    SCREENSHOT;

    public static StepType parse(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("步骤类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("不支持的步骤类型: " + value);
        }
    }

    /** Steps that need a selector / target to be meaningful. */
    public boolean requiresSelector() {
        return switch (this) {
            case CLICK, INPUT, TAP, SWIPE, LAUNCH, ASSERT_TEXT, ASSERT_EXISTS -> true;
            default -> false;
        };
    }
}
