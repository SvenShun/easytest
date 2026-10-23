package com.testhub.core.feature.system;

/** Model configuration update payload; blank API key keeps the stored secret. */
public record UpdateModelConfigRequest(String provider, String endpoint, String apiKey, String modelId) {
}
