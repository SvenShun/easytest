package com.testhub.core.feature.plan;

import java.time.Instant;

/** Run counters exactly as defined by contract §1.7. */
public record PlanRunView(String id, String planId, String status, String triggeredBy, Instant startedAt,
                          Instant finishedAt, int total, int success, int failed, int timeout, int cancelled,
                          int running, int pending, int skipped) {

    public static PlanRunView of(PlanRun run) {
        return new PlanRunView(run.getId(), run.getPlanId(),
                run.getStatus() == null ? null : run.getStatus().name(), run.getTriggeredBy(),
                run.getStartedAt(), run.getFinishedAt(), run.getTotal(), run.getSuccess(), run.getFailed(),
                run.getTimeout(), run.getCancelled(), run.getRunning(), run.getPending(), run.getSkipped());
    }
}
