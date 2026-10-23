package com.testhub.uiexec.routing;

import com.testhub.uiexec.domain.ExecutorType;

/** Inputs for executor routing — kept free of persistence and HTTP so it is unit-testable. */
public record RoutingInput(boolean approved, boolean hasScript, ExecutorType requested, boolean visionConfigured) {

    public static RoutingInput ofReviewStatus(String reviewStatus, boolean hasScript, ExecutorType requested,
                                              boolean visionConfigured) {
        return new RoutingInput("APPROVED".equalsIgnoreCase(reviewStatus), hasScript, requested, visionConfigured);
    }
}
