package com.testhub.agent.runtime;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.agent.config.AgentProperties;
import com.testhub.agent.dispatch.AgentTask;
import com.testhub.agent.dispatch.TaskDispatchRequest;
import com.testhub.agent.dispatch.TaskReporter;
import com.testhub.agent.dispatch.TaskStatus;
import com.testhub.agent.http.HttpJsonClient;
import com.testhub.agent.skills.SkillsCache;
import com.testhub.agent.skills.SkillsUnavailableException;

/**
 * Bridges AI Runtime tasks: resolves Skills, calls {@code POST /runtime/tasks},
 * waits for the synchronous result and reports it to ui-execution. Steps and
 * logs are callback'd by the Runtime directly, so the result payload here only
 * carries the terminating status.
 */
@Component
public class RuntimeBridge {

    private static final Logger log = LoggerFactory.getLogger(RuntimeBridge.class);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);
    private static final long HEALTH_CACHE_MS = 5_000L;
    private static final Duration CANCEL_TIMEOUT = Duration.ofSeconds(15);

    private final AgentProperties properties;
    private final HttpJsonClient http;
    private final SkillsCache skillsCache;
    private final TaskReporter taskReporter;

    private volatile boolean runtimeReachable;
    private volatile long lastHealthCheck;

    public RuntimeBridge(AgentProperties properties, HttpJsonClient http, SkillsCache skillsCache,
            TaskReporter taskReporter) {
        this.properties = properties;
        this.http = http;
        this.skillsCache = skillsCache;
        this.taskReporter = taskReporter;
    }

    public void execute(AgentTask task, TaskDispatchRequest request) {
        String token = request.callbackToken(properties.getToken());
        if (task.isCancelRequested()) {
            reportCancelled(task, request, "task cancelled before runtime dispatch");
            return;
        }

        String skillsDir = null;
        if (request.skills() != null && request.skills().commit() != null
                && !request.skills().commit().isBlank()) {
            try {
                Path resolved = skillsCache.resolve(request.skills().repo(), request.skills().commit());
                skillsDir = resolved == null ? null : resolved.toString();
                if (skillsDir != null) {
                    log.info("task {} using skills {} @ {}", task.getTaskId(),
                            SkillsCache.maskRepo(request.skills().repo()), request.skills().commit());
                }
            } catch (SkillsUnavailableException e) {
                log.warn("task {} skills unavailable: {}", task.getTaskId(), e.getMessage());
                taskReporter.report(task, request, TaskStatus.FAILED, "SKILLS_UNAVAILABLE",
                        "skills unavailable: " + e.getMessage(), List.of());
                return;
            }
        }

        if (task.isCancelRequested()) {
            reportCancelled(task, request, "task cancelled before runtime dispatch");
            return;
        }

        String runtimeUrl = properties.runtimeUrlNoTrailingSlash();
        if (runtimeUrl.isBlank()) {
            taskReporter.report(task, request, TaskStatus.FAILED, "EXECUTOR_UNAVAILABLE",
                    "runtime url is not configured", List.of());
            return;
        }

        Map<String, Object> payload = buildPayload(task, request, skillsDir, token);
        task.setCancelHandler(() -> cancelRuntimeTask(task, request, token));
        if (task.isCancelRequested()) {
            // Cancel arrived before the runtime call was issued.
            task.setCancelHandler(null);
            reportCancelled(task, request, "task cancelled before runtime dispatch");
            return;
        }
        try {
            log.info("task {} dispatching to runtime {}", task.getTaskId(), runtimeUrl);
            HttpJsonClient.Response response = http.postJson(runtimeUrl + "/runtime/tasks", payload,
                    Map.of(), Duration.ofSeconds(request.totalSec() + 30L));

            if (task.isResultReported()) {
                log.info("task {} result already reported while runtime call returned", task.getTaskId());
                return;
            }
            if (task.isCancelRequested()) {
                // The cancel handler owns the CANCELLED result.
                return;
            }
            if (!response.ok()) {
                String message = "runtime returned HTTP " + response.status();
                log.warn("task {} {}", task.getTaskId(), message);
                taskReporter.report(task, request, TaskStatus.FAILED, "EXECUTOR_UNAVAILABLE",
                        message, List.of());
                return;
            }

            JsonNode body = http.readTree(response.body());
            String status = body.path("status").asText("");
            String reasonCategory = nullableText(body, "reasonCategory");
            String summary = body.path("summary").asText("");
            int totalSteps = body.path("totalSteps").asInt(0);
            long elapsedMs = body.path("elapsedMs").asLong(0);
            log.info("task {} runtime finished: status={} reason={} steps={} elapsedMs={}",
                    task.getTaskId(), status, reasonCategory, totalSteps, elapsedMs);

            if (summary.isBlank()) {
                summary = "runtime finished with status " + (status.isBlank() ? "UNKNOWN" : status);
            }
            switch (status) {
                case TaskStatus.SUCCESS -> taskReporter.report(task, request, TaskStatus.SUCCESS,
                        null, summary, List.of());
                case TaskStatus.FAILED -> taskReporter.report(task, request, TaskStatus.FAILED,
                        reasonCategory == null ? "ACTION_FAILED" : reasonCategory, summary, List.of());
                case TaskStatus.TIMEOUT -> taskReporter.report(task, request, TaskStatus.TIMEOUT,
                        "TIMEOUT", summary, List.of());
                case TaskStatus.CANCELLED -> taskReporter.report(task, request, TaskStatus.CANCELLED,
                        "MANUAL_INTERRUPT", summary, List.of());
                default -> taskReporter.report(task, request, TaskStatus.FAILED,
                        "EXECUTOR_UNAVAILABLE",
                        "runtime returned unknown status: " + (status.isBlank() ? "<empty>" : status),
                        List.of());
            }
        } catch (RuntimeException e) {
            if (task.isResultReported()) {
                return;
            }
            if (task.isCancelRequested()) {
                // The cancel handler reports CANCELLED after the runtime cancel call.
                log.info("task {} runtime call aborted because cancel was requested", task.getTaskId());
                return;
            }
            String message = "runtime unavailable: " + rootMessage(e);
            log.warn("task {} {}", task.getTaskId(), message);
            taskReporter.report(task, request, TaskStatus.FAILED, "EXECUTOR_UNAVAILABLE",
                    message, List.of());
        } finally {
            if (!task.isResultReported() && task.isCancelRequested()) {
                reportCancelled(task, request, "task cancelled by request");
            }
            task.setCancelHandler(null);
        }
    }

    private void cancelRuntimeTask(AgentTask task, TaskDispatchRequest request, String token) {
        String runtimeUrl = properties.runtimeUrlNoTrailingSlash();
        if (!runtimeUrl.isBlank()) {
            String cancelUrl = runtimeUrl + "/runtime/tasks/"
                    + URLEncoder.encode(task.getTaskId(), StandardCharsets.UTF_8) + "/cancel";
            try {
                HttpJsonClient.Response response = http.postJson(cancelUrl, Map.of(), Map.of(),
                        CANCEL_TIMEOUT);
                log.info("task {} runtime cancel returned HTTP {}", task.getTaskId(), response.status());
            } catch (RuntimeException e) {
                log.warn("task {} runtime cancel call failed: {}", task.getTaskId(), e.getMessage());
            }
        }
        reportCancelled(task, request, "task cancelled by request");
    }

    private void reportCancelled(AgentTask task, TaskDispatchRequest request, String summary) {
        taskReporter.report(task, request, TaskStatus.CANCELLED, "MANUAL_INTERRUPT", summary, List.of());
    }

    public Map<String, Object> buildPayload(AgentTask task, TaskDispatchRequest request,
            String skillsDir, String token) {
        TaskDispatchRequest.Bdd bdd = request.bdd();
        Map<String, Object> bddPayload = new LinkedHashMap<>();
        bddPayload.put("given", bdd == null ? "" : bdd.safeGiven());
        bddPayload.put("when", bdd == null ? "" : bdd.safeWhen());
        bddPayload.put("then", bdd == null ? "" : bdd.safeThen());

        Map<String, Object> skills = new LinkedHashMap<>();
        skills.put("repo", request.skills() == null ? null : request.skills().repo());
        skills.put("commit", request.skills() == null ? null : request.skills().commit());
        skills.put("dir", skillsDir);

        Map<String, Object> timeouts = new LinkedHashMap<>();
        timeouts.put("totalSec", request.totalSec());
        timeouts.put("stepSec", request.stepSec());

        TaskDispatchRequest.Model model = request.model();
        Map<String, Object> modelPayload = new LinkedHashMap<>();
        modelPayload.put("provider", model == null ? "mock" : model.safeProvider());
        modelPayload.put("endpoint", model == null ? "" : model.safeEndpoint());
        modelPayload.put("apiKey", model == null ? "" : model.safeApiKey());
        modelPayload.put("modelId", model == null ? "mock-vision" : model.safeModelId());

        Map<String, Object> device = new LinkedHashMap<>();
        device.put("baseUrl", properties.baseUrlNoTrailingSlash());
        device.put("serial", request.deviceSerial());

        Map<String, Object> callbacks = new LinkedHashMap<>();
        TaskDispatchRequest.Callbacks requestCallbacks = request.callbacks();
        callbacks.put("stepUrl", requestCallbacks == null ? null : requestCallbacks.stepUrl());
        callbacks.put("logUrl", requestCallbacks == null ? null : requestCallbacks.logUrl());
        callbacks.put("token", token);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("bdd", bddPayload);
        payload.put("skills", skills);
        payload.put("timeouts", timeouts);
        payload.put("maxSteps", TaskDispatchRequest.MAX_STEPS);
        payload.put("model", modelPayload);
        payload.put("device", device);
        payload.put("callbacks", callbacks);
        return payload;
    }

    /** Cached runtime health probe used by {@code GET /agent/health}. */
    public boolean isReachable() {
        String runtimeUrl = properties.runtimeUrlNoTrailingSlash();
        if (runtimeUrl.isBlank()) {
            return false;
        }
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (now - lastHealthCheck < HEALTH_CACHE_MS) {
                return runtimeReachable;
            }
            try {
                HttpJsonClient.Response response = http.get(runtimeUrl + "/runtime/health",
                        Map.of(), HEALTH_TIMEOUT);
                runtimeReachable = response.ok();
            } catch (RuntimeException e) {
                runtimeReachable = false;
            }
            lastHealthCheck = now;
            return runtimeReachable;
        }
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
