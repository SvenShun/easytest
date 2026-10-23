package com.testhub.core.feature.system;

import java.time.Instant;

/** Plaintext credential handed to trusted internal callers only. */
public record ModelCredential(String configType, String provider, String endpoint, String apiKey,
                              String modelId, Instant expiresAt) {
}
