package com.easytest.core.feature.apiagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Capability/load registry for API execution agents, backed by the shared database. */
@Component
public class ApiAgentRegistry {

    private static final long STALE_SECONDS = 35;
    private final Map<String, Agent> local = new ConcurrentHashMap<>();
    private final ApiAgentRepository repository;
    private final ObjectMapper objectMapper;

    public ApiAgentRegistry() {
        this(null, new ObjectMapper());
    }

    @Autowired
    public ApiAgentRegistry(ApiAgentRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public synchronized Agent register(RegisterRequest request) {
        String baseUrl = normalize(request.baseUrl());
        String id = blank(request.agentId()) ? baseUrl : request.agentId().trim();
        Agent agent = new Agent(id, blank(request.name()) ? id : request.name().trim(), baseUrl,
                normalizeCapabilities(request.capabilities()), Math.max(1, request.slots() == null ? 1 : request.slots()),
                Math.max(0, request.activeTasks() == null ? 0 : request.activeTasks()), "UP", Instant.now());
        if (repository == null) {
            local.put(id, agent);
            return agent;
        }
        ApiAgentEntity entity = repository.findById(id).orElseGet(() ->
                new ApiAgentEntity(id, agent.name(), baseUrl, json(agent.capabilities()), agent.slots(),
                        agent.activeTasks(), "UP", agent.lastSeenAt(), Instant.now()));
        entity.setName(agent.name());
        entity.setBaseUrl(baseUrl);
        entity.setCapabilitiesJson(json(agent.capabilities()));
        entity.setSlots(agent.slots());
        entity.setActiveTasks(agent.activeTasks());
        entity.setStatus("UP");
        entity.setLastSeenAt(agent.lastSeenAt());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return agent(entity);
    }

    @Transactional
    public synchronized Agent heartbeat(HeartbeatRequest request) {
        String id = request.agentId();
        if (repository == null) {
            Agent current = local.get(id);
            if (current == null) {
                return register(new RegisterRequest(id, request.name(), request.baseUrl(),
                        request.capabilities(), request.slots(), request.activeTasks()));
            }
            Agent next = new Agent(current.agentId(), current.name(),
                    blank(request.baseUrl()) ? current.baseUrl() : normalize(request.baseUrl()),
                    request.capabilities() == null ? current.capabilities() : normalizeCapabilities(request.capabilities()),
                    request.slots() == null ? current.slots() : Math.max(1, request.slots()),
                    request.activeTasks() == null ? current.activeTasks() : Math.max(0, request.activeTasks()),
                    "UP", Instant.now());
            local.put(id, next);
            return next;
        }
        ApiAgentEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            return register(new RegisterRequest(id, request.name(), request.baseUrl(),
                    request.capabilities(), request.slots(), request.activeTasks()));
        }
        if (request.baseUrl() != null && !request.baseUrl().isBlank()) entity.setBaseUrl(normalize(request.baseUrl()));
        if (request.capabilities() != null) entity.setCapabilitiesJson(json(normalizeCapabilities(request.capabilities())));
        if (request.slots() != null) entity.setSlots(Math.max(1, request.slots()));
        if (request.activeTasks() != null) entity.setActiveTasks(Math.max(0, request.activeTasks()));
        // DRAINING is sticky until the agent explicitly re-registers.
        entity.setLastSeenAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return agent(entity);
    }

    @Transactional
    public void drain(String agentId) {
        if (repository == null) {
            Agent current = local.get(agentId);
            if (current != null) {
                local.put(agentId, new Agent(current.agentId(), current.name(), current.baseUrl(),
                        current.capabilities(), current.slots(), current.activeTasks(), "DRAINING",
                        current.lastSeenAt()));
            }
            return;
        }
        repository.updateStatus(agentId, "DRAINING", Instant.now());
    }

    @Transactional
    public synchronized void remove(String agentId) {
        if (repository == null) {
            local.remove(agentId);
        } else {
            repository.deleteById(agentId);
        }
    }

    public synchronized boolean hasAgents() {
        return !all().isEmpty();
    }

    public List<Agent> all() {
        if (repository == null) {
            return local.values().stream().sorted(Comparator.comparing(Agent::agentId)).toList();
        }
        return repository.findAll().stream().map(this::agent)
                .sorted(Comparator.comparing(Agent::agentId)).toList();
    }

    /** Atomically reserves one slot on the least-loaded matching agent. */
    public synchronized String reserve(Set<String> requiredCapabilities) {
        List<Agent> available = available(requiredCapabilities);
        if (available.isEmpty()) return null;
        Agent selected = available.get(0);
        return reserveAt(selected.baseUrl()) ? selected.baseUrl() : null;
    }

    @Transactional
    public synchronized boolean reserveAt(String baseUrl) {
        if (repository == null) {
            Agent agent = local.values().stream().filter(item -> baseUrl.equals(item.baseUrl())).findFirst().orElse(null);
            if (agent == null || agent.activeTasks() >= agent.slots() || "DRAINING".equals(agent.status())) return false;
            local.put(agent.agentId(), new Agent(agent.agentId(), agent.name(), agent.baseUrl(), agent.capabilities(),
                    agent.slots(), agent.activeTasks() + 1, agent.status(), agent.lastSeenAt()));
            return true;
        }
        ApiAgentEntity entity = repository.findAll().stream()
                .filter(item -> baseUrl.equals(item.getBaseUrl()) && "UP".equals(item.getStatus())).findFirst().orElse(null);
        return entity != null && repository.reserve(entity.getAgentId()) == 1;
    }

    @Transactional
    public synchronized void release(String baseUrl) {
        if (baseUrl == null) return;
        if (repository == null) {
            Agent agent = local.values().stream().filter(item -> baseUrl.equals(item.baseUrl())).findFirst().orElse(null);
            if (agent == null) return;
            local.put(agent.agentId(), new Agent(agent.agentId(), agent.name(), agent.baseUrl(),
                    agent.capabilities(), agent.slots(), Math.max(0, agent.activeTasks() - 1), agent.status(),
                    agent.lastSeenAt()));
            return;
        }
        repository.findAll().stream().filter(item -> baseUrl.equals(item.getBaseUrl())).findFirst()
                .ifPresent(item -> repository.release(item.getAgentId()));
    }

    public List<String> availableBaseUrls(Set<String> requiredCapabilities) {
        return available(requiredCapabilities).stream().map(Agent::baseUrl).toList();
    }

    private List<Agent> available(Set<String> requiredCapabilities) {
        Set<String> required = requiredCapabilities == null ? Set.of() : requiredCapabilities;
        Instant cutoff = Instant.now().minusSeconds(STALE_SECONDS);
        List<Agent> candidates = repository == null
                ? local.values().stream().filter(agent -> agent.lastSeenAt().isAfter(cutoff)).toList()
                : repository.findByStatusAndLastSeenAtAfterOrderByActiveTasksAsc("UP", cutoff)
                        .stream().map(this::agent).toList();
        return candidates.stream()
                .filter(agent -> agent.activeTasks() < agent.slots())
                .filter(agent -> agent.capabilities().containsAll(required))
                .sorted(Comparator.comparingInt(Agent::activeTasks)
                        .thenComparing(Agent::lastSeenAt, Comparator.reverseOrder()))
                .filter(agent -> !agent.baseUrl().isBlank())
                .toList();
    }

    private Agent agent(ApiAgentEntity entity) {
        return new Agent(entity.getAgentId(), entity.getName(), entity.getBaseUrl(),
                readCapabilities(entity.getCapabilitiesJson()), entity.getSlots(), entity.getActiveTasks(),
                entity.getStatus(), entity.getLastSeenAt());
    }

    private String json(Set<String> capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities == null ? List.of() : capabilities);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Set<String> readCapabilities(String json) {
        if (json == null || json.isBlank()) return Set.of();
        try {
            return new LinkedHashSet<>(objectMapper.readValue(json, new TypeReference<List<String>>() { }));
        } catch (Exception e) {
            return Set.of();
        }
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
                        int slots, int activeTasks, String status, Instant lastSeenAt) {
    }
}
