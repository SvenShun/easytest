package com.testhub.uiexec.web;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.uiexec.domain.AgentEntity;
import com.testhub.uiexec.domain.DeviceEntity;
import com.testhub.uiexec.domain.DeviceStatus;
import com.testhub.uiexec.service.AgentTokenGuard;
import com.testhub.uiexec.service.DeviceService;
import com.testhub.uiexec.web.dto.AgentDtos.AgentRegisterRequest;
import com.testhub.uiexec.web.dto.AgentDtos.AgentRegisterResponse;
import com.testhub.uiexec.web.dto.AgentDtos.AgentView;
import com.testhub.uiexec.web.dto.AgentDtos.DeviceBrief;
import com.testhub.uiexec.web.dto.AgentDtos.DeviceHeartbeatView;
import com.testhub.uiexec.web.dto.AgentDtos.HeartbeatResponse;
import com.testhub.uiexec.web.dto.AgentDtos.AgentUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Agent registration / heartbeat and agent listing (docs/API-CONTRACT.md §2.4). */
@RestController
@RequestMapping("/api/v1/ui-execution/agents")
public class AgentController {

    private final DeviceService deviceService;
    private final AgentTokenGuard agentTokenGuard;

    public AgentController(DeviceService deviceService, AgentTokenGuard agentTokenGuard) {
        this.deviceService = deviceService;
        this.agentTokenGuard = agentTokenGuard;
    }

    @PostMapping("/register")
    public AgentRegisterResponse register(@RequestBody AgentRegisterRequest request,
                                          HttpServletRequest httpRequest) {
        agentTokenGuard.require(httpRequest);
        AgentEntity agent = deviceService.register(request.name(), request.host(), request.baseUrl(),
                request.devices());
        List<DeviceBrief> devices = deviceService.devicesOf(agent.getId()).stream()
                .map(device -> new DeviceBrief(device.getId(), device.getSerial()))
                .toList();
        return new AgentRegisterResponse(agent.getId(), devices);
    }

    @PostMapping("/{agentId}/heartbeat")
    public HeartbeatResponse heartbeat(@PathVariable String agentId,
                                       @RequestBody(required = false) AgentRegisterRequest request,
                                       HttpServletRequest httpRequest) {
        agentTokenGuard.require(httpRequest);
        AgentEntity agent = deviceService.heartbeat(agentId, request == null ? null : request.devices());
        List<DeviceHeartbeatView> devices = deviceService.devicesOf(agent.getId()).stream()
                .map(device -> new DeviceHeartbeatView(device.getId(), device.getSerial(),
                        deviceService.statusOf(device) != DeviceStatus.OFFLINE))
                .toList();
        return new HeartbeatResponse(agent.getId(), deviceService.agentStatus(agent), devices);
    }

    @PutMapping("/{agentId}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public AgentView update(@PathVariable String agentId, @RequestBody AgentUpdateRequest request) {
        return view(deviceService.updateAgent(agentId, request.name(), request.enabled()));
    }

    @GetMapping
    public List<AgentView> list() {
        return deviceService.listAgents().stream().map(this::view).toList();
    }

    private AgentView view(AgentEntity agent) {
        return new AgentView(agent.getId(), agent.getName(), agent.getHost(), agent.getBaseUrl(),
                deviceService.agentStatus(agent), agent.isEnabled(), agent.getLastHeartbeat(),
                deviceService.devicesOf(agent.getId()).stream().map(deviceService::toView).toList());
    }
}
