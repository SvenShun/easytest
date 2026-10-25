package com.testhub.core.feature.plan;

/** Create / update payload for a plan. */
public record PlanRequest(String name, String description, String environmentId) {
}
