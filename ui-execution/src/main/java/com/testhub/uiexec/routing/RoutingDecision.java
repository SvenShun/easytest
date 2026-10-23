package com.testhub.uiexec.routing;

import com.testhub.uiexec.domain.ExecutorType;

/**
 * Routing result.
 *
 * <ul>
 *   <li>{@link #rejected()} — task must not be created (409 with {@link #errorCode()}).</li>
 *   <li>{@link #immediateFailure()} — task is created but immediately FAILED (still returns taskId).</li>
 *   <li>otherwise — task is created with {@link #executorType()}.</li>
 * </ul>
 */
public record RoutingDecision(ExecutorType executorType, String errorCode, String message, String note,
                              boolean immediateFailure) {

    public static RoutingDecision ok(ExecutorType executorType) {
        return new RoutingDecision(executorType, null, null, null, false);
    }

    public static RoutingDecision fallback(ExecutorType executorType, String reason) {
        return new RoutingDecision(executorType, null, null, reason, false);
    }

    public static RoutingDecision reject(String errorCode, String message) {
        return new RoutingDecision(null, errorCode, message, null, false);
    }

    public static RoutingDecision immediateFailure(ExecutorType executorType, String errorCode, String message) {
        return new RoutingDecision(executorType, errorCode, message, message, true);
    }

    public boolean rejected() {
        return errorCode != null && !immediateFailure;
    }

    public boolean ok() {
        return errorCode == null;
    }

    public boolean hasFallback() {
        return note != null && errorCode == null;
    }
}
