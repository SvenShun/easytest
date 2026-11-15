package com.easytest.core.feature.engineering;

import java.time.Instant;
import java.util.List;

public final class EngineeringMethodDtos {

    private EngineeringMethodDtos() {
    }

    public record MethodView(String id, String contractId, String name, String description, String methodPath,
                             List<String> inputs, List<String> outputs, List<String> tags,
                             String source, Instant updatedAt, String projectId, String repoId,
                             String kind, String payloadJson, String layer, List<String> protocols) {
    }

    public record ManifestImportRequest(String yaml) {
    }

    public record SyncResult(int imported, int total, List<String> errors) {
    }
}
