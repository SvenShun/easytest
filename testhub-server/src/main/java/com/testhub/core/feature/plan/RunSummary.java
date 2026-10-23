package com.testhub.core.feature.plan;

public record RunSummary(int total, int success, int failed, int timeout, int cancelled, int running,
                         int pending, int skipped) {

    public static RunSummary of(PlanRun run) {
        return new RunSummary(run.getTotal(), run.getSuccess(), run.getFailed(), run.getTimeout(),
                run.getCancelled(), run.getRunning(), run.getPending(), run.getSkipped());
    }
}
