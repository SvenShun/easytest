package com.testhub.uiexec.events;

import com.testhub.uiexec.domain.DeviceEntity;
import com.testhub.uiexec.domain.TaskEntity;

import java.time.Instant;

/**
 * Task lifecycle event. The JSON shape is the cross-service contract for Kafka
 * topics {@code ui-execution.task.dispatch} and {@code ui-execution.task.result}
 * (docs/API-CONTRACT.md appendix); it is also emitted by the local publisher so
 * single-node deployments keep the same observability data in the log.
 */
public record TaskEvent(
        String eventId,
        String type,
        String taskId,
        String planId,
        String planRunId,
        String caseId,
        String caseTitle,
        String executorType,
        String status,
        String deviceId,
        String deviceSerial,
        String agentId,
        String failureReason,
        String failureReportUrl,
        String summary,
        Instant occurredAt) {

    public static TaskEvent dispatched(TaskEntity task, DeviceEntity device, String eventId) {
        return of("TASK_DISPATCHED", task, device, eventId);
    }

    public static TaskEvent finished(TaskEntity task, DeviceEntity device, String eventId) {
        return of("TASK_FINISHED", task, device, eventId);
    }

    private static TaskEvent of(String type, TaskEntity task, DeviceEntity device, String eventId) {
        return new TaskEvent(
                eventId,
                type,
                task.getId(),
                task.getPlanId(),
                task.getPlanRunId(),
                task.getCaseId(),
                task.getCaseTitle(),
                task.getExecutorType() == null ? null : task.getExecutorType().name(),
                task.getStatus() == null ? null : task.getStatus().name(),
                device == null ? task.getDeviceId() : device.getId(),
                device == null ? task.getDeviceSerial() : device.getSerial(),
                device == null ? task.getAgentId() : device.getAgentId(),
                task.getFailureReason() == null ? null : task.getFailureReason().name(),
                task.getFailureReportUrl(),
                task.getFailureSummary(),
                Instant.now());
    }
}
