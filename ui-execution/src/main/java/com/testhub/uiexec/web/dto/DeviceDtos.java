package com.testhub.uiexec.web.dto;

import com.testhub.uiexec.domain.DeviceStatus;
import com.testhub.uiexec.domain.ExecutorType;
import com.testhub.uiexec.domain.TaskStatus;

import java.time.Instant;
import java.util.List;

/** Device / queue response shapes (docs/API-CONTRACT.md §2.4). */
public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record DeviceView(String deviceId, String serial, String name, String model, String platform,
                             List<String> tags, DeviceStatus status, boolean online, String currentTaskId,
                             int queueLength, String agentId, String agentName, Instant lastSeenAt) {
    }

    public record DeviceListResponse(List<DeviceView> items, long total, int page, int size) {
    }

    public record QueueTaskView(String taskId, int position, String caseTitle, TaskStatus status,
                                ExecutorType executorType, Instant createdAt) {
    }

    public record QueueView(String deviceId, String serial, int queueLength, List<QueueTaskView> tasks) {
    }
}
