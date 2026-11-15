package com.easytest.core.feature.apiagent;

import com.easytest.common.security.RequireRole;
import com.easytest.common.security.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Read-only API execution agent registry for the platform UI. */
@RestController
@RequestMapping("/api/v1/api-agents")
@RequireRole({Role.ADMIN, Role.TESTER})
public class ApiAgentController {

    private final ApiAgentRegistry registry;

    public ApiAgentController(ApiAgentRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<ApiAgentRegistry.Agent> list() {
        return registry.all();
    }

    @DeleteMapping("/{agentId}")
    @RequireRole({Role.ADMIN})
    public void remove(@PathVariable String agentId) {
        registry.remove(agentId);
    }

    @PostMapping("/{agentId}/drain")
    @RequireRole({Role.ADMIN})
    public Map<String, Object> drain(@PathVariable String agentId) {
        registry.drain(agentId);
        return Map.of("agentId", agentId, "status", "DRAINING");
    }
}
