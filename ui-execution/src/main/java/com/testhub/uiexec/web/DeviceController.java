package com.testhub.uiexec.web;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.uiexec.client.AgentClient;
import com.testhub.uiexec.domain.AgentEntity;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

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
    private final AgentClient agentClient;

    public DeviceController(DeviceService deviceService, TaskRepository taskRepository, DeviceQueueStore queue,
                            AgentClient agentClient) {
        this.deviceService = deviceService;
        this.taskRepository = taskRepository;
        this.queue = queue;
        this.agentClient = agentClient;
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

    @PostMapping("/{deviceId}/occupy")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public DeviceView occupy(@PathVariable String deviceId) {
        return deviceService.occupy(deviceId, com.testhub.common.security.CurrentUser.require().username());
    }

    @PostMapping("/{deviceId}/release")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public DeviceView release(@PathVariable String deviceId) {
        return deviceService.release(deviceId, com.testhub.common.security.CurrentUser.require().username());
    }

    @GetMapping("/{deviceId}/screenshot")
    public ResponseEntity<byte[]> screenshot(@PathVariable String deviceId) {
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(agentClient.screenshot(agent.getBaseUrl(), device.getSerial()));
    }

    @GetMapping("/{deviceId}/ui-tree")
    public Object uiTree(@PathVariable String deviceId) {
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        return agentClient.uiTree(agent.getBaseUrl(), device.getSerial());
    }

    @PostMapping("/{deviceId}/shell")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public Object shell(@PathVariable String deviceId, @RequestBody Map<String, Object> body) {
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        String command = body == null ? null : String.valueOf(body.get("command"));
        if (command == null || command.isBlank()) {
            throw com.testhub.common.web.ApiException.badRequest("command 不能为空");
        }
        int timeout = body.get("timeoutSec") instanceof Number number ? number.intValue() : 30;
        return agentClient.shell(agent.getBaseUrl(), device.getSerial(), command, timeout);
    }

    @GetMapping("/{deviceId}/apps")
    public Object apps(@PathVariable String deviceId) {
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        return agentClient.apps(agent.getBaseUrl(), device.getSerial());
    }

    @PostMapping("/{deviceId}/uninstall")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public Object uninstall(@PathVariable String deviceId, @RequestParam String packageName) {
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        return agentClient.uninstall(agent.getBaseUrl(), device.getSerial(), packageName);
    }

    @PostMapping("/{deviceId}/apps/{action}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public Object appAction(@PathVariable String deviceId, @PathVariable String action,
                            @RequestParam String packageName) {
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        if (!action.equals("launch") && !action.equals("stop") && !action.equals("clear")) {
            throw com.testhub.common.web.ApiException.badRequest("action 仅支持 launch/stop/clear");
        }
        return agentClient.appAction(agent.getBaseUrl(), device.getSerial(), action, packageName);
    }

    @PostMapping("/{deviceId}/rotate")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public Object rotate(@PathVariable String deviceId,
                         @RequestParam(defaultValue = "auto") String direction) {
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        return agentClient.rotate(agent.getBaseUrl(), device.getSerial(), direction);
    }

    @PostMapping("/{deviceId}/files")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public Object pushFile(@PathVariable String deviceId,
                           @RequestParam("file") MultipartFile file,
                           @RequestParam(required = false) String remotePath) throws IOException {
        if (file == null || file.isEmpty()) {
            throw com.testhub.common.web.ApiException.badRequest("文件为空");
        }
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        return agentClient.pushFile(agent.getBaseUrl(), device.getSerial(), file.getBytes(),
                file.getOriginalFilename(), remotePath);
    }

    @GetMapping("/{deviceId}/performance")
    public Object performance(@PathVariable String deviceId) {
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        return agentClient.performance(agent.getBaseUrl(), device.getSerial());
    }

    @PostMapping("/{deviceId}/install")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public Object install(@PathVariable String deviceId,
                          @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw com.testhub.common.web.ApiException.badRequest("APK 文件为空");
        }
        if (file.getSize() > 200L * 1024 * 1024) {
            throw com.testhub.common.web.ApiException.badRequest("APK 超过 200MB");
        }
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        return agentClient.install(agent.getBaseUrl(), device.getSerial(), file.getBytes(),
                file.getOriginalFilename());
    }

    @PostMapping("/{deviceId}/action")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public Object action(@PathVariable String deviceId, @RequestBody Map<String, Object> action) {
        DeviceEntity device = deviceService.requireDevice(deviceId);
        AgentEntity agent = deviceService.requireAgent(device);
        return agentClient.action(agent.getBaseUrl(), device.getSerial(), action);
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
