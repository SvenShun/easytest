package com.testhub.uiexec.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Allowed task state transitions (docs/API-CONTRACT.md §6):
 * <pre>
 * PENDING    -> DISPATCHED | FAILED | CANCELLED
 * DISPATCHED -> RUNNING | SUCCESS | FAILED | TIMEOUT | CANCELLED
 * RUNNING    -> SUCCESS | FAILED | TIMEOUT | CANCELLED
 * terminal   -> (none)
 * </pre>
 */
public final class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS;

    static {
        Map<TaskStatus, Set<TaskStatus>> map = new EnumMap<>(TaskStatus.class);
        map.put(TaskStatus.PENDING, EnumSet.of(TaskStatus.DISPATCHED, TaskStatus.FAILED, TaskStatus.CANCELLED));
        map.put(TaskStatus.DISPATCHED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.SUCCESS, TaskStatus.FAILED,
                TaskStatus.TIMEOUT, TaskStatus.CANCELLED));
        map.put(TaskStatus.RUNNING, EnumSet.of(TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.TIMEOUT,
                TaskStatus.CANCELLED));
        map.put(TaskStatus.SUCCESS, Collections.emptySet());
        map.put(TaskStatus.FAILED, Collections.emptySet());
        map.put(TaskStatus.TIMEOUT, Collections.emptySet());
        map.put(TaskStatus.CANCELLED, Collections.emptySet());
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private TaskStateMachine() {
    }

    public static boolean isTerminal(TaskStatus status) {
        return status != null && status.isTerminal();
    }

    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    public static void requireTransition(TaskStatus from, TaskStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("非法任务状态流转: " + from + " -> " + to);
        }
    }
}
