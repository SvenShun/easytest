package com.testhub.uiexec.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** GET /internal/v1/model-configs/vision/credential. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelCredential(String configType, String provider, String endpoint, String apiKey, String modelId,
                              String expiresAt) {
}
