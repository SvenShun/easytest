package com.testhub.uiexec.web.dto;

import java.time.Instant;

/** Skills pointer shapes (docs/API-CONTRACT.md §2.5). */
public final class SkillsDtos {

    private SkillsDtos() {
    }

    public record SkillsPointerUpdateRequest(String gitUrl, String branch) {
    }

    public record SkillsRollbackRequest(String commit) {
    }

    public record SkillsPointerView(String gitUrl, String branch, String activeCommit, String updatedBy,
                                    Instant updatedAt, Instant syncedAt, boolean configured) {
    }

    public record SkillsSyncResponse(String activeCommit, Instant syncedAt) {
    }
}
