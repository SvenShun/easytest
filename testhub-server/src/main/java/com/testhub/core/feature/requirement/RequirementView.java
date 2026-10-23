package com.testhub.core.feature.requirement;

import java.time.Instant;

public record RequirementView(String id, String title, String description, String createdBy, Instant createdAt,
                              Instant updatedAt) {

    public static RequirementView of(Requirement requirement) {
        return new RequirementView(requirement.getId(), requirement.getTitle(), requirement.getDescription(),
                requirement.getCreatedBy(), requirement.getCreatedAt(), requirement.getUpdatedAt());
    }
}
