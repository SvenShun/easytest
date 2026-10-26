package com.testhub.uiexec.service;

import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.uiexec.config.UiExecProperties;
import com.testhub.uiexec.domain.AgentEntity;
import com.testhub.uiexec.domain.DeviceEntity;
import com.testhub.uiexec.domain.DeviceStatus;
import com.testhub.uiexec.domain.ExecutorType;
import com.testhub.uiexec.domain.TaskEntity;
import com.testhub.uiexec.domain.TaskStatus;
import com.testhub.uiexec.queue.DeviceQueueStore;
import com.testhub.uiexec.repository.AgentRepository;
import com.testhub.uiexec.repository.DeviceRepository;
import com.testhub.uiexec.repository.TaskRepository;
import com.testhub.uiexec.web.dto.AgentDtos.AgentDeviceRequest;
import com.testhub.uiexec.web.dto.DeviceDtos.DeviceView;
import com.testhub.uiexec.web.dto.TaskDtos.TargetDeviceSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Agent/device registry, heartbeat handling, computed device status and device selection. */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private static final List<TaskStatus> ACTIVE_STATUSES = List.of(TaskStatus.DISPATCHED, TaskStatus.RUNNING);

    private final AgentRepository agents;
    private final DeviceRepository devices;
    private final TaskRepository tasks;
    private final DeviceQueueStore queue;
    private final UiExecProperties properties;

    public DeviceService(AgentRepository agents, DeviceRepository devices, TaskRepository tasks,
                         DeviceQueueStore queue, UiExecProperties properties) {
        this.agents = agents;
        this.devices = devices;
        this.tasks = tasks;
        this.queue = queue;
        this.properties = properties;
    }

    // ---------------------------------------------------------------- agents

    public synchronized AgentEntity register(String name, String host, String baseUrl,
                                             List<AgentDeviceRequest> deviceRequests) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Agent 名称不能为空");
        }
        Instant now = Instant.now();
        AgentEntity agent = agents.findByName(name.trim())
                .orElseGet(() -> new AgentEntity(Ids.newId("agent"), name.trim(), host, baseUrl, now));
        agent.setHost(host);
        agent.setBaseUrl(baseUrl);
        agent.setLastHeartbeat(now);
        AgentEntity saved = agents.save(agent);
        upsertDevices(saved, deviceRequests);
        return saved;
    }

    public synchronized AgentEntity heartbeat(String agentId, List<AgentDeviceRequest> deviceRequests) {
        AgentEntity agent = agents.findById(agentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
        agent.setLastHeartbeat(Instant.now());
        AgentEntity saved = agents.save(agent);
        upsertDevices(saved, deviceRequests);
        return saved;
    }

    private void upsertDevices(AgentEntity agent, List<AgentDeviceRequest> requests) {
        if (requests == null) {
            return;
        }
        Instant now = Instant.now();
        for (AgentDeviceRequest request : requests) {
            if (request == null || request.serial() == null || request.serial().isBlank()) {
                log.warn("忽略缺少 serial 的设备上报 agentId={}", agent.getId());
                continue;
            }
            DeviceEntity device = devices.findByAgentIdAndSerial(agent.getId(), request.serial().trim())
                    .orElseGet(() -> new DeviceEntity(Ids.newId("device"), agent.getId(), request.serial().trim()));
            device.setName(request.name());
            device.setModel(request.model());
            device.setPlatform(request.platform());
            device.setTags(joinTags(request.tags()));
            device.setOnline(request.online() == null || request.online());
            device.setLastSeenAt(now);
            devices.save(device);
        }
    }

    public AgentEntity requireAgent(DeviceEntity device) {
        return agents.findById(device.getAgentId())
                .orElseThrow(() -> ApiException.notFound("设备所属 Agent 不存在: " + device.getAgentId()));
    }

    public List<AgentEntity> listAgents() {
        return agents.findAll();
    }

    public synchronized AgentEntity updateAgent(String agentId, String name, Boolean enabled) {
        AgentEntity agent = requireAgent(agentId);
        if (name != null && !name.isBlank()) {
            String trimmed = name.trim();
            agents.findByName(trimmed).ifPresent(other -> {
                if (!other.getId().equals(agentId)) {
                    throw ApiException.conflict("Agent 名称已存在: " + trimmed);
                }
            });
            agent.setName(trimmed);
        }
        if (enabled != null) {
            agent.setEnabled(enabled);
        }
        return agents.save(agent);
    }

    public AgentEntity requireAgent(String agentId) {
        return agents.findById(agentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
    }

    public List<DeviceEntity> devicesOf(String agentId) {
        return devices.findByAgentId(agentId);
    }

    public String agentStatus(AgentEntity agent) {
        if (agent != null && !agent.isEnabled()) {
            return "DISABLED";
        }
        return isAgentOnline(agent) ? "ONLINE" : "OFFLINE";
    }

    public boolean isAgentOnline(AgentEntity agent) {
        if (agent == null || !agent.isEnabled() || agent.getLastHeartbeat() == null) {
            return false;
        }
        return agent.getLastHeartbeat().isAfter(Instant.now().minusSeconds(properties.getAgentOfflineSeconds()));
    }

    // --------------------------------------------------------------- devices

    public List<DeviceEntity> listDevices() {
        return devices.findAll();
    }

    public Optional<DeviceEntity> findDevice(String deviceId) {
        return deviceId == null ? Optional.empty() : devices.findById(deviceId);
    }

    public DeviceEntity requireDevice(String deviceId) {
        return devices.findById(deviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "设备不存在"));
    }

    /**
     * Selection priority: explicit deviceId → devices of the requested agent →
     * devices matching all tags → any online device. Within the winning tier the
     * shortest queue wins (idle devices have queue length 0).
     */
    public DeviceEntity selectDevice(TargetDeviceSelector selector) {
        List<DeviceEntity> all = devices.findAll();
        if (all.isEmpty()) {
            return null;
        }
        TargetDeviceSelector criteria = selector == null
                ? new TargetDeviceSelector(null, null, List.of())
                : selector;

        if (criteria.deviceId() != null && !criteria.deviceId().isBlank()) {
            DeviceEntity explicit = findDevice(criteria.deviceId().trim()).orElse(null);
            if (explicit != null && isDispatchable(explicit)) {
                return explicit;
            }
            log.warn("显式指定的设备不可用，按优先级继续选择 deviceId={}", criteria.deviceId());
        }
        if (criteria.agentId() != null && !criteria.agentId().isBlank()) {
            List<DeviceEntity> candidates = all.stream()
                    .filter(device -> criteria.agentId().trim().equals(device.getAgentId()))
                    .filter(this::isDispatchable)
                    .toList();
            if (!candidates.isEmpty()) {
                return pickShortestQueue(candidates);
            }
        }
        List<String> tags = normalizeTags(criteria.tags());
        if (!tags.isEmpty()) {
            List<DeviceEntity> candidates = all.stream()
                    .filter(this::isDispatchable)
                    .filter(device -> tagsMatch(device, tags))
                    .toList();
            if (!candidates.isEmpty()) {
                return pickShortestQueue(candidates);
            }
        }
        List<DeviceEntity> anyOnline = all.stream().filter(this::isDispatchable).toList();
        return anyOnline.isEmpty() ? null : pickShortestQueue(anyOnline);
    }

    private DeviceEntity pickShortestQueue(List<DeviceEntity> candidates) {
        List<DeviceEntity> copy = new ArrayList<>(candidates);
        copy.sort(Comparator.comparingInt((DeviceEntity device) -> queueLength(device.getId()))
                .thenComparing(device -> device.getId() == null ? "" : device.getId()));
        return copy.get(0);
    }

    public boolean isDispatchable(DeviceEntity device) {
        DeviceStatus status = statusOf(device);
        return status != DeviceStatus.OFFLINE && status != DeviceStatus.MAINTENANCE;
    }

    public DeviceStatus statusOf(DeviceEntity device) {
        if (device == null) {
            return DeviceStatus.OFFLINE;
        }
        if (device.getAgentId() != null) {
            AgentEntity owner = agents.findById(device.getAgentId()).orElse(null);
            if (owner != null && !owner.isEnabled()) {
                return DeviceStatus.OFFLINE;
            }
        }
        boolean heartbeatStale = device.getLastSeenAt() == null
                || device.getLastSeenAt().isBefore(Instant.now().minusSeconds(properties.getAgentOfflineSeconds()));
        if (!device.isOnline() || heartbeatStale) {
            return DeviceStatus.OFFLINE;
        }
        TaskEntity current = currentTask(device.getId());
        if (current != null) {
            return current.getExecutorType() == ExecutorType.SCRIPT
                    ? DeviceStatus.SCRIPT_RUNNING
                    : DeviceStatus.AI_RUNNING;
        }
        if (!queue.list(device.getId()).isEmpty()) {
            return DeviceStatus.QUEUED;
        }
        return DeviceStatus.IDLE;
    }

    public TaskEntity currentTask(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        return tasks.findFirstByDeviceIdAndStatusInOrderByDispatchedAtDesc(deviceId, ACTIVE_STATUSES).orElse(null);
    }

    public int queueLength(String deviceId) {
        return queue.list(deviceId).size();
    }

    public DeviceView toView(DeviceEntity device) {
        AgentEntity agent = device.getAgentId() == null ? null
                : agents.findById(device.getAgentId()).orElse(null);
        TaskEntity current = currentTask(device.getId());
        DeviceStatus status = statusOf(device);
        return new DeviceView(
                device.getId(),
                device.getSerial(),
                device.getName(),
                device.getModel(),
                device.getPlatform(),
                splitTags(device.getTags()),
                status,
                status != DeviceStatus.OFFLINE && status != DeviceStatus.MAINTENANCE,
                current == null ? null : current.getId(),
                queueLength(device.getId()),
                device.getAgentId(),
                agent == null ? null : agent.getName(),
                device.getLastSeenAt(),
                activeOccupant(device), activeOccupant(device) == null ? null : device.getOccupiedUntil());
    }

    public synchronized DeviceView occupy(String deviceId, String user) {
        DeviceEntity device = requireDevice(deviceId);
        Instant now = Instant.now();
        if (device.getOccupant() != null && device.getOccupiedUntil() != null
                && device.getOccupiedUntil().isAfter(now) && !device.getOccupant().equals(user)) {
            throw ApiException.conflict("设备已被 " + device.getOccupant() + " 占用");
        }
        device.setOccupant(user);
        device.setOccupiedUntil(now.plusSeconds(600));
        devices.save(device);
        return toView(device);
    }

    public synchronized DeviceView release(String deviceId, String user) {
        DeviceEntity device = requireDevice(deviceId);
        if (device.getOccupant() != null && !device.getOccupant().equals(user)
                && !com.testhub.common.security.CurrentUser.isAdmin()) {
            throw ApiException.conflict("只有占用者或管理员可以释放设备");
        }
        device.setOccupant(null);
        device.setOccupiedUntil(null);
        devices.save(device);
        return toView(device);
    }

    private String activeOccupant(DeviceEntity device) {
        if (device.getOccupant() == null || device.getOccupiedUntil() == null
                || device.getOccupiedUntil().isBefore(Instant.now())) {
            return null;
        }
        return device.getOccupant();
    }

    public static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String tag : tags.split(",")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private boolean tagsMatch(DeviceEntity device, List<String> required) {
        Set<String> owned = new LinkedHashSet<>();
        for (String tag : splitTags(device.getTags())) {
            owned.add(tag.toLowerCase(Locale.ROOT));
        }
        return owned.containsAll(required);
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                normalized.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(normalized);
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                cleaned.add(tag.trim());
            }
        }
        return String.join(",", cleaned);
    }
}
