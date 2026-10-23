package com.testhub.uiexec.web.dto;

import java.time.Instant;
import java.util.List;

/** Agent registration / heartbeat shapes (docs/API-CONTRACT.md §2.4). */
public final class AgentDtos {

    private AgentDtos() {
    }

    public record AgentDeviceRequest(String serial, String name, String model, String platform, List<String> tags,
                                     Boolean online) {
    }

    public record AgentRegisterRequest(String name, String host, String baseUrl, List<AgentDeviceRequest> devices) {
    }

    public record DeviceBrief(String deviceId, String serial) {
    }

    public record AgentRegisterResponse(String agentId, List<DeviceBrief> devices) {
    }

    public record DeviceHeartbeatView(String deviceId, String serial, boolean online) {
    }

    public record HeartbeatResponse(String agentId, String status, List<DeviceHeartbeatView> devices) {
    }

    public record AgentView(String agentId, String name, String host, String baseUrl, String status,
                            Instant lastHeartbeat, List<DeviceDtos.DeviceView> devices) {
    }
}
