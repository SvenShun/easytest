package com.testhub.core.feature.ai;

import java.util.List;

/** Everything a text provider needs for one generation request. */
public record TextModelInput(String provider, String endpoint, String apiKey, String modelId,
                             String requirementText, List<BddSample> samples) {
}
