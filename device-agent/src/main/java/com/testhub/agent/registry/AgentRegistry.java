package com.testhub.agent.registry;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.agent.config.AgentProperties;
import com.testhub.agent.driver.DeviceManager;
import com.testhub.agent.http.HttpJsonClient;

import jakarta.annotation.PreDestroy;

/**
 * Registers this agent with ui-execution and keeps it alive with heartbeats.
 * Registration is asynchronous with exponential backoff (max 60s) so a missing
 * ui-execution never blocks agent startup.
 */
@Component
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);
    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 60_000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final AgentProperties properties;
    private final DeviceManager deviceManager;
    private final HttpJsonClient http;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean registrationPending = new AtomicBoolean(false);

    private volatile String agentId;
    private volatile boolean uiExecutionReachable;
    private volatile Instant lastHeartbeatAt;
    private volatile String lastRegistrationError;
    private volatile long backoffMs = INITIAL_BACKOFF_MS;

    public AgentRegistry(AgentProperties properties, DeviceManager deviceManager, HttpJsonClient http) {
        this.properties = properties;
        this.deviceManager = deviceManager;
        this.http = http;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-registry");
            thread.setDaemon(true);
            return thread;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.isRegistrationEnabled()) {
            log.info("agent registration disabled by configuration");
            return;
        }
        log.info("agent '{}' registering with ui-execution at {} (devices={})",
                properties.getName(), properties.uiExecutionUrlNoTrailingSlash(),
                deviceManager.descriptors().size());
        scheduleRegistration(0L);
    }

    /** Heartbeat every {@code testhub.agent.heartbeat-interval-sec} (default 20s). */
    @Scheduled(fixedDelayString = "${testhub.agent.heartbeat-interval-sec:20}000")
    public void heartbeat() {
        if (!properties.isRegistrationEnabled()) {
            return;
        }
        String currentAgentId = agentId;
        if (currentAgentId == null) {
            return;
        }
        String url = properties.uiExecutionUrlNoTrailingSlash()
                + "/api/v1/ui-execution/agents/" + currentAgentId + "/heartbeat";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("devices", deviceManager.descriptors());
        try {
            HttpJsonClient.Response response = http.postJson(url, body, tokenHeaders(), REQUEST_TIMEOUT);
            if (response.ok()) {
                uiExecutionReachable = true;
                lastHeartbeatAt = Instant.now();
                lastRegistrationError = null;
                log.debug("heartbeat sent for agent {}", currentAgentId);
            } else {
                uiExecutionReachable = false;
                log.warn("heartbeat for agent {} failed: HTTP {}", currentAgentId, response.status());
                if (response.status() == 404) {
                    log.warn("ui-execution no longer knows agent {}; re-registering", currentAgentId);
                    agentId = null;
                    backoffMs = INITIAL_BACKOFF_MS;
                    scheduleRegistration(backoffMs);
                }
            }
        } catch (RuntimeException e) {
            uiExecutionReachable = false;
            log.warn("heartbeat for agent {} failed: {}", currentAgentId, e.getMessage());
        }
    }

    private void scheduleRegistration(long delayMs) {
        if (!properties.isRegistrationEnabled()) {
            return;
        }
        if (!registrationPending.compareAndSet(false, true)) {
            return;
        }
        scheduler.schedule(() -> {
            registrationPending.set(false);
            try {
                if (registerOnce()) {
                    return;
                }
            } catch (Exception e) {
                log.warn("agent registration failed: {}", e.getMessage());
                uiExecutionReachable = false;
                lastRegistrationError = e.getMessage();
            }
            long delay = Math.min(MAX_BACKOFF_MS, Math.max(INITIAL_BACKOFF_MS, backoffMs));
            backoffMs = Math.min(MAX_BACKOFF_MS, backoffMs * 2);
            log.warn("retrying agent registration in {}ms", delay);
            scheduleRegistration(delay);
        }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
    }

    private boolean registerOnce() {
        if (agentId != null) {
            return true;
        }
        String url = properties.uiExecutionUrlNoTrailingSlash() + "/api/v1/ui-execution/agents/register";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", properties.getName());
        body.put("host", properties.getHost());
        body.put("baseUrl", properties.getBaseUrl());
        body.put("devices", deviceManager.descriptors());
        try {
            HttpJsonClient.Response response = http.postJson(url, body, tokenHeaders(), REQUEST_TIMEOUT);
            if (!response.ok()) {
                uiExecutionReachable = false;
                lastRegistrationError = "HTTP " + response.status();
                log.warn("agent registration failed: HTTP {} {}", response.status(), response.body());
                return false;
            }
            JsonNode json = http.readTree(response.body());
            String id = json.path("agentId").asText("");
            if (id.isBlank()) {
                uiExecutionReachable = false;
                lastRegistrationError = "register response has no agentId";
                log.warn("agent registration failed: response has no agentId");
                return false;
            }
            agentId = id;
            uiExecutionReachable = true;
            lastRegistrationError = null;
            backoffMs = INITIAL_BACKOFF_MS;
            lastHeartbeatAt = Instant.now();
            log.info("agent registered: agentId={} name={} devices={}", id, properties.getName(),
                    deviceManager.descriptors().size());
            return true;
        } catch (RuntimeException e) {
            uiExecutionReachable = false;
            lastRegistrationError = e.getMessage();
            log.warn("agent registration failed: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, String> tokenHeaders() {
        return Map.of("X-Agent-Token", properties.getToken() == null ? "" : properties.getToken());
    }

    public String getAgentId() {
        return agentId;
    }

    public boolean isUiExecutionReachable() {
        return uiExecutionReachable;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public String getLastRegistrationError() {
        return lastRegistrationError;
    }

    public List<String> deviceSerials() {
        return deviceManager.serials();
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
