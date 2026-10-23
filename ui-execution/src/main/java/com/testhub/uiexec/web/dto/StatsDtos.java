package com.testhub.uiexec.web.dto;

import java.time.Instant;
import java.util.List;

/** Statistics + audit log shapes (docs/API-CONTRACT.md §2.5). */
public final class StatsDtos {

    private StatsDtos() {
    }

    public record DeviceStats(long total, long online, long idle, long running, long offline) {
    }

    public record TaskStats(long pending, long running, long success, long failed, long timeout, long cancelled) {
    }

    public record FailureCount(String category, long count) {
    }

    public record SkillsStats(boolean configured, String activeCommit) {
    }

    public record StatsView(DeviceStats devices, TaskStats tasks, List<FailureCount> failureCategories,
                            SkillsStats skills) {
    }

    public record AuditLogView(String id, String actor, String action, String target, String detail,
                               Instant createdAt) {
    }
}
