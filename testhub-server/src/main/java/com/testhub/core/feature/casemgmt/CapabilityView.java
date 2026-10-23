package com.testhub.core.feature.casemgmt;

import com.testhub.core.feature.plan.ExecutorType;

import java.util.List;

/** Executors the case can currently run on. */
public record CapabilityView(List<ExecutorType> availableExecutors, ExecutorType defaultExecutor) {
}
