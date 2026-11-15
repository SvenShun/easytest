package com.easytest.core.feature.project;

import java.time.Instant;
import java.util.List;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectRequest(String name, String key, String description) {
    }

    public record MemberRequest(String username, String role) {
    }

    public record ProjectView(String id, String name, String key, String description, String createdBy,
                              Instant createdAt, Instant updatedAt, int memberCount) {
    }

    public record MemberView(String id, String username, String role, Instant createdAt) {
    }

    public record ProjectDetail(ProjectView project, List<MemberView> members) {
    }
}
