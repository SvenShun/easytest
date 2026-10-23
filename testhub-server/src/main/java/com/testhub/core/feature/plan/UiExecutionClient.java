package com.testhub.core.feature.plan;

import java.util.List;
import java.util.Map;

/**
 * Port for the ui-execution service. Kept as an interface so plan triggering can
 * be tested without a live ui-execution instance.
 */
public interface UiExecutionClient {

    /** Creates an execution task; throws when ui-execution rejects or is unreachable. */
    TaskCreated createTask(Map<String, Object> payload);

    /** Lists task summaries of one plan run (used for reconciliation). */
    List<TaskSnapshot> listTasksByPlanRun(String planRunId);

    record TaskCreated(String taskId, String status, String executorType, String deviceId) {
    }

    record TaskSnapshot(String taskId, String status, String executorType, String deviceId,
                        String failureReason, String failureReportUrl, String summary) {
    }
}
