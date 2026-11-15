package com.easytest.apiexec.agent;

import com.easytest.apiexec.config.ApiExecProperties;
import com.easytest.apiexec.service.TaskWorkerPool;
import com.easytest.common.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registers this API executor as a capability/load-aware API agent at easytest-server. */
@Component
public class ApiAgentReporter {

    private static final Logger log = LoggerFactory.getLogger(ApiAgentReporter.class);

    private final ApiExecProperties properties;
    private final TaskWorkerPool workers;
    private final RestClient client;

    public ApiAgentReporter(ApiExecProperties properties, TaskWorkerPool workers,
                            SecurityProperties securityProperties) {
        this.properties = properties;
        this.workers = workers;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1_000);
        factory.setReadTimeout(1_000);
        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("X-Service-Token", securityProperties.getServiceToken())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        report(true);
    }

    @Scheduled(fixedDelay = 10_000)
    public void heartbeat() {
        report(false);
    }

    private void report(boolean register) {
        if (!properties.isAgentRegistrationEnabled()) return;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("agentId", properties.getInstanceId());
            body.put("name", properties.getInstanceId());
            body.put("baseUrl", properties.getPublicBaseUrl());
            body.put("capabilities", properties.getAgentCapabilities() == null
                    ? List.of("HTTP") : properties.getAgentCapabilities());
            body.put("slots", Math.max(1, properties.getWorkers()));
            body.put("activeTasks", workers.activeTasks());
            client.post()
                    .uri(properties.getCoreBaseUrl() + (register ? "/internal/v1/api-agents/register"
                            : "/internal/v1/api-agents/heartbeat"))
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            log.info("API agent {} core server", register ? "registered at" : "heartbeat sent to");
        } catch (RuntimeException e) {
            log.warn("API agent registration/heartbeat unavailable: {}", e.getMessage());
        }
    }
}
