package com.testhub.agent.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.testhub.agent.driver.DeviceDescriptor;
import com.testhub.agent.driver.DeviceManager;
import com.testhub.agent.registry.AgentRegistry;
import com.testhub.agent.runtime.RuntimeBridge;

/** Agent health and local device inventory. */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final DeviceManager deviceManager;
    private final AgentRegistry registry;
    private final RuntimeBridge runtimeBridge;

    public AgentController(DeviceManager deviceManager, AgentRegistry registry,
            RuntimeBridge runtimeBridge) {
        this.deviceManager = deviceManager;
        this.registry = registry;
        this.runtimeBridge = runtimeBridge;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        List<DeviceDescriptor> devices = deviceManager.descriptors();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("mode", deviceManager.mode());
        body.put("devices", devices);
        body.put("runtimeReachable", runtimeBridge.isReachable());
        body.put("uiExecutionReachable", registry.isUiExecutionReachable());
        body.put("agentId", registry.getAgentId());
        return body;
    }

    @PostMapping("/devices/refresh")
    public Map<String, Object> refreshDevices() {
        deviceManager.refresh();
        try {
            registry.heartbeat();
        } catch (RuntimeException e) {
            // hotplug still succeeds locally even when ui-execution is unreachable
        }
        return devices();
    }

    @GetMapping("/devices")
    public Map<String, Object> devices() {
        List<DeviceDescriptor> devices = deviceManager.descriptors();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("devices", devices);
        // Compatibility alias for clients that use the contract's list convention.
        body.put("items", devices);
        body.put("total", devices.size());
        return body;
    }
}
