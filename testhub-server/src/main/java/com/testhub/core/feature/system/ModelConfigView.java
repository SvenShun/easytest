package com.testhub.core.feature.system;

import java.time.Instant;

/** Configuration view; the raw API key is never returned. */
public record ModelConfigView(String configType, String provider, String endpoint, String modelId,
                              String apiKeyMasked, boolean configured, String updatedBy, Instant updatedAt) {
}
