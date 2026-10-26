package com.testhub.core.feature.casemgmt;

import com.testhub.common.web.ApiException;

import java.util.Locale;

/** How a step selector is interpreted. {@code COORD} holds {@code "x,y"} (or four points for SWIPE). */
public enum SelectorType {
    TEXT,
    ID,
    COORD;

    public static SelectorType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("selectorType 仅支持 TEXT / ID / COORD");
        }
    }
}
