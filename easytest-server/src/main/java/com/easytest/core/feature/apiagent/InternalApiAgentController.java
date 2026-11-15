package com.easytest.core.feature.apiagent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Service-token authenticated registration endpoint used by API agents. */
@RestController
@RequestMapping("/internal/v1/api-agents")
public class InternalApiAgentController {

    private final ApiAgentRegistry registry;

    public InternalApiAgentController(ApiAgentRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/register")
    public ApiAgentRegistry.Agent register(@RequestBody ApiAgentRegistry.RegisterRequest request) {
        return registry.register(request);
    }

    @PostMapping("/heartbeat")
    public ApiAgentRegistry.Agent heartbeat(@RequestBody ApiAgentRegistry.HeartbeatRequest request) {
        return registry.heartbeat(request);
    }

    @GetMapping
    public List<ApiAgentRegistry.Agent> list() {
        return registry.all();
    }
}
