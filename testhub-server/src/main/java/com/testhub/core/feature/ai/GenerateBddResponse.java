package com.testhub.core.feature.ai;

import java.util.List;

public record GenerateBddResponse(String jobId, String status, String modelId, List<BddDraftResult> drafts) {
}
