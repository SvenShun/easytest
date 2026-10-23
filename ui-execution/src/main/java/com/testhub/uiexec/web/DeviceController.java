package com.testhub.uiexec.web;

import com.testhub.uiexec.domain.DeviceEntity;
import com.testhub.uiexec.domain.DeviceStatus;
import com.testhub.uiexec.domain.TaskEntity;
import com.testhub.uiexec.queue.DeviceQueueStore;
import com.testhub.uiexec.repository.TaskRepository;
import com.testhub.uiexec.service.DeviceService;
import com.testhub.uiexec.web.dto.DeviceDtos.DeviceListResponse;
import com.testhub.uiexec.web.dto.DeviceDtos.DeviceView;
import com.testhub.uiexec.web.dto.DeviceDtos.QueueTaskView;
import com.testhub.uiexec.web.dto.DeviceDtos.QueueView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Device pool and per-device queue views (docs/API-CONTRACT.md §2.4). */
@RestController
@RequestMapping("/api/v1/ui-execution/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final TaskRepository taskRepository;
    private final DeviceQueueStore queue;

    public DeviceController(DeviceService deviceService, TaskRepository taskRepository, DeviceQueueStore queue) {
        this.deviceService = deviceService;
        this.taskRepository = taskRepository;
        this.queue = queue;
    }

    @GetMapping
    public DeviceListResponse list(@RequestParam(required = false) String status,
                                   @RequestParam(required = false) String keyword) {
        DeviceStatus filter = parseStatus(status);
        String needle = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<DeviceView> items = new ArrayList<>();
        for (DeviceEntity device : deviceService.listDevices()) {
            DeviceView view = deviceService.toView(device);
            if (filter != null && view.status() != filter) {
                continue;
            }
            if (needle != null && !needle.isBlank() && !matchesKeyword(device, view, needle)) {
                continue;
            }
            items.add(view);
        }
        return new DeviceListResponse(items, items.size(), 1, items.size());
    }

    @GetMapping("/{deviceId}/queue")
    public QueueView queue(@PathVariable String deviceId) {
        DeviceEntity device = deviceService.requireDevice(deviceId);
        List<String> taskIds = queue.list(deviceId);
        List<QueueTaskView> items = new ArrayList<>();
        int position = 0;
        for (String taskId : taskIds) {
            position++;
            TaskEntity task = taskRepository.findById(taskId).orElse(null);
            if (task == null) {
                continue;
            }
            items.add(new QueueTaskView(task.getId(), position, task.getCaseTitle(), task.getStatus(),
                    task.getExecutorType(), task.getCreatedAt()));
        }
        return new QueueView(device.getId(), device.getSerial(), items.size(), items);
    }

    private boolean matchesKeyword(DeviceEntity device, DeviceView view, String needle) {
        return contains(view.serial(), needle)
                || contains(view.name(), needle)
                || contains(view.model(), needle)
                || contains(view.deviceId(), needle)
                || view.tags().stream().anyMatch(tag -> contains(tag, needle));
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private DeviceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DeviceStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法的设备状态: " + status);
        }
    }
}
