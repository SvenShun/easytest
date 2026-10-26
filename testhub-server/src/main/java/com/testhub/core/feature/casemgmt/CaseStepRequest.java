package com.testhub.core.feature.casemgmt;

/**
 * One declarative step in a case create/update request.
 *
 * <p>{@code seq} is optional: when omitted the position in {@code steps} is used. {@code enabled}
 * defaults to {@code true}.</p>
 */
public record CaseStepRequest(Integer seq, String stepType, String selectorType, String selector,
                              String value, String assertValue, Integer timeoutMs, Boolean enabled) {

    public CaseStepRequest(String stepType, String selectorType, String selector, String value,
                           String assertValue, Integer timeoutMs) {
        this(null, stepType, selectorType, selector, value, assertValue, timeoutMs, null);
    }
}
