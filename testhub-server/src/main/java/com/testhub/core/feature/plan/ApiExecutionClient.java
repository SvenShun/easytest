package com.testhub.core.feature.plan;

import java.util.List;
import java.util.Map;

/** Port for api-execution workers (round-robin across configured base URLs). */
public interface ApiExecutionClient {

    TaskCreated createTask(Map<String, Object> payload);

    List<TaskSnapshot> listTasksByPlanRun(String planRunId);

    record TaskCreated(String taskId, String status) {
    }

    record TaskSnapshot(String taskId, String status, String failureReason, String failureReportUrl,
                        String summary) {
    }
}
