package com.testhub.core.feature.plan;

import java.util.List;

/** Trigger options; null values fall back to the contract defaults. */
public record TriggerRequest(String targetDeviceId, List<String> targetTags, Integer timeoutSec,
                             Integer stepTimeoutSec) {
}
