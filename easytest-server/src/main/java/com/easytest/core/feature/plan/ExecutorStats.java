package com.easytest.core.feature.plan;

public record ExecutorStats(ExecutorType executorType, int total, int success, int failed) {
}
