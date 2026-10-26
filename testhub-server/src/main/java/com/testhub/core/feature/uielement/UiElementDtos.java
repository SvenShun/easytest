package com.testhub.core.feature.uielement;

import java.time.Instant;

public final class UiElementDtos {

    private UiElementDtos() {
    }

    public record ElementRequest(String name, String elementType, String elementValue, String description) {
    }

    public record ElementView(String id, String name, String elementType, String elementValue,
                              String description, String createdBy, Instant createdAt, Instant updatedAt) {
    }
}
