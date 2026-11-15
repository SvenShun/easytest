package com.easytest.core.feature.plan;

import java.util.List;
import java.util.Map;

/** Port for api-execution workers (round-robin across configured base URLs). */
public interface ApiExecutionClient {

    TaskCreated createTask(Map<String, Object> payload);

    List<TaskSnapshot> listTasksByPlanRun(String planRunId);

    List<EngineeringMethod> listEngineeringMethods();

    /** Installs an engineering JAR on API agents and reloads contract methods. */
    void uploadEngineeringArtifact(String filename, byte[] content);

    record EngineeringMethod(String id, String name, String description, String methodPath,
                            List<String> inputs, List<String> outputs, List<String> tags,
                            String layer, List<String> protocols) {
    }

    record TaskCreated(String taskId, String status) {
    }

    record TaskSnapshot(String taskId, String status, String failureReason, String failureReportUrl,
                        String summary) {
    }
}
