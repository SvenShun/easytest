package com.testhub.core.feature.casemgmt;

/** Step projection returned inside {@link CaseView}, ordered by {@code seq}. */
public record CaseStepView(String id, int seq, StepType stepType, SelectorType selectorType, String selector,
                           String value, String assertValue, int timeoutMs, boolean enabled) {

    public static CaseStepView of(CaseStep step) {
        return new CaseStepView(step.getId(), step.getSeq(), step.getStepType(), step.getSelectorType(),
                step.getSelector(), step.getValue(), step.getAssertValue(), step.getTimeoutMs(), step.isEnabled());
    }
}
