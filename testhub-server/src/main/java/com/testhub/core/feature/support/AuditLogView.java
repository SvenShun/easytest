package com.testhub.core.feature.support;

import java.time.Instant;

/** Audit log projection. */
public record AuditLogView(String id, String actor, String action, String target, String detail,
                           Instant createdAt) {

    public static AuditLogView of(AuditLog log) {
        return new AuditLogView(log.getId(), log.getActor(), log.getAction(), log.getTarget(), log.getDetail(),
                log.getCreatedAt());
    }
}
