package com.easytest.core.feature.apiagent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAgentRegistryTest {

    @Test
    void selectsCapabilityMatchAndLowestLoad() {
        ApiAgentRegistry registry = new ApiAgentRegistry();
        registry.register(new ApiAgentRegistry.RegisterRequest("a1", "agent-1", "http://127.0.0.1:8084",
                List.of("HTTP", "SQL"), 4, 3));
        registry.register(new ApiAgentRegistry.RegisterRequest("a2", "agent-2", "http://127.0.0.1:8085",
                List.of("HTTP", "COMPONENT"), 4, 0));
        registry.register(new ApiAgentRegistry.RegisterRequest("a3", "agent-3", "http://127.0.0.1:8086",
                List.of("SQL"), 2, 0));

        assertThat(registry.availableBaseUrls(Set.of("HTTP"))).containsExactly("http://127.0.0.1:8085",
                "http://127.0.0.1:8084");
        assertThat(registry.availableBaseUrls(Set.of("COMPONENT"))).containsExactly("http://127.0.0.1:8085");
        assertThat(registry.availableBaseUrls(Set.of("SQL"))).containsExactly("http://127.0.0.1:8086",
                "http://127.0.0.1:8084");
    }

    @Test
    void heartbeatUpdatesLoadAndUnknownAgentRegisters() {
        ApiAgentRegistry registry = new ApiAgentRegistry();
        registry.register(new ApiAgentRegistry.RegisterRequest("a1", "agent-1", "http://127.0.0.1:8084",
                List.of("HTTP"), 1, 1));
        assertThat(registry.availableBaseUrls(Set.of("HTTP"))).isEmpty();
        registry.heartbeat(new ApiAgentRegistry.HeartbeatRequest("a1", "agent-1", null,
                List.of("HTTP"), 2, 0));
        assertThat(registry.availableBaseUrls(Set.of("HTTP"))).containsExactly("http://127.0.0.1:8084");
        registry.heartbeat(new ApiAgentRegistry.HeartbeatRequest("new", "new", "http://127.0.0.1:8087",
                List.of("HTTP"), 1, 0));
        assertThat(registry.all()).hasSize(2);
    }
}
