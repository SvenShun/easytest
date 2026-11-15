package com.easytest.core.feature.apiagent;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory capability/load registry for API execution agents. */
@Component
public class ApiAgentRegistry {

    private static final long STALE_SECONDS = 35;

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    public synchronized Agent register(RegisterRequest request) {
        String baseUrl = normalize(request.baseUrl());
        String id = blank(request.agentId()) ? baseUrl : request.agentId().trim();
        Agent agent = new Agent(id, blank(request.name()) ? id : request.name().trim(), baseUrl,
                normalizeCapabilities(request.capabilities()), Math.max(1, request.slots() == null ? 1 : request.slots()),
                Math.max(0, request.activeTasks() == null ? 0 : request.activeTasks()), Instant.now());
        agents.put(id, agent);
        return agent;
    }

    public synchronized Agent heartbeat(HeartbeatRequest request) {
        String id = request.agentId();
        Agent current = agents.get(id);
        if (current == null) {
            return register(new RegisterRequest(id, request.name(), request.baseUrl(),
                    request.capabilities(), request.slots(), request.activeTasks()));
        }
        Agent next = new Agent(current.agentId(), current.name(),
                blank(request.baseUrl()) ? current.baseUrl() : normalize(request.baseUrl()),
                request.capabilities() == null ? current.capabilities() : normalizeCapabilities(request.capabilities()),
                request.slots() == null ? current.slots() : Math.max(1, request.slots()),
                request.activeTasks() == null ? current.activeTasks() : Math.max(0, request.activeTasks()),
                Instant.now());
        agents.put(id, next);
        return next;
    }

    public boolean hasAgents() {
        evictStale();
        return !agents.isEmpty();
    }

    public List<Agent> all() {
        evictStale();
        return agents.values().stream().sorted(Comparator.comparing(Agent::agentId)).toList();
    }

    /** Atomically reserves one slot on the least-loaded matching agent. */
    public synchronized String reserve(Set<String> requiredCapabilities) {
        evictStale();
        Set<String> required = requiredCapabilities == null ? Set.of() : requiredCapabilities;
        Agent selected = agents.values().stream()
                .filter(agent -> agent.activeTasks() < agent.slots())
                .filter(agent -> agent.capabilities().containsAll(required))
                .min(Comparator.comparingInt(Agent::activeTasks)
                        .thenComparing(Agent::lastSeenAt))
                .orElse(null);
        if (selected == null) return null;
        Agent next = new Agent(selected.agentId(), selected.name(), selected.baseUrl(),
                selected.capabilities(), selected.slots(), selected.activeTasks() + 1, selected.lastSeenAt());
        agents.put(selected.agentId(), next);
        return selected.baseUrl();
    }

    public synchronized void release(String baseUrl) {
        if (baseUrl == null) return;
        Agent agent = agents.values().stream().filter(item -> baseUrl.equals(item.baseUrl())).findFirst().orElse(null);
        if (agent == null) return;
        agents.put(agent.agentId(), new Agent(agent.agentId(), agent.name(), agent.baseUrl(),
                agent.capabilities(), agent.slots(), Math.max(0, agent.activeTasks() - 1), agent.lastSeenAt()));
    }

    public List<String> availableBaseUrls(Set<String> requiredCapabilities) {
        evictStale();
        Set<String> required = requiredCapabilities == null ? Set.of() : requiredCapabilities;
        return agents.values().stream()
                .filter(agent -> agent.activeTasks() < agent.slots())
                .filter(agent -> agent.capabilities().containsAll(required))
                .sorted(Comparator.comparingInt(Agent::activeTasks)
                        .thenComparing(Agent::lastSeenAt, Comparator.reverseOrder()))
                .map(Agent::baseUrl)
                .filter(url -> !url.isBlank())
                .toList();
    }

    private void evictStale() {
        Instant cutoff = Instant.now().minusSeconds(STALE_SECONDS);
        agents.entrySet().removeIf(entry -> entry.getValue().lastSeenAt().isBefore(cutoff));
    }

    private static Set<String> normalizeCapabilities(List<String> capabilities) {
        if (capabilities == null) return Set.of();
        return capabilities.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalize(String url) {
        if (url == null || url.isBlank()) return "";
        String value = url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record RegisterRequest(String agentId, String name, String baseUrl, List<String> capabilities,
                                  Integer slots, Integer activeTasks) {
    }

    public record HeartbeatRequest(String agentId, String name, String baseUrl, List<String> capabilities,
                                   Integer slots, Integer activeTasks) {
    }

    public record Agent(String agentId, String name, String baseUrl, Set<String> capabilities,
                        int slots, int activeTasks, Instant lastSeenAt) {
    }
}
