package com.testhub.core.feature.caselink;

import java.time.Instant;

public final class CaseAutomationLinkDtos {

    private CaseAutomationLinkDtos() {
    }

    /** targetType is API_CASE | API_SCENARIO | UI_CASE. */
    public record LinkRequest(String targetType, String targetId) {
    }

    public record LinkView(String id, String targetType, String targetId, String targetName,
                           boolean targetEnabled, String latestStatus, String createdBy,
                           Instant createdAt) {
    }
}
