package com.testhub.apiexec.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.apiexec.client.CoreCallbackClient;
import com.testhub.apiexec.client.PlanItemResultPayload;
import com.testhub.apiexec.config.ApiExecProperties;
import com.testhub.apiexec.domain.ApiTaskStatus;
import com.testhub.apiexec.queue.CancellationStore;
import com.testhub.apiexec.queue.TaskQueue;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lifecycle for queued YAML scenario tasks. */
@Service
public class ScenarioExecutionService {

    public static final String QUEUE_PREFIX = "scenario:";
    private static final Logger log = LoggerFactory.getLogger(ScenarioExecutionService.class);

    private final ScenarioTaskRepository tasks;
    private final ScenarioYamlParser parser;
    private final ScenarioExecutor executor;
    private final TaskQueue queue;
    private final CancellationStore cancellationStore;
    private final CoreCallbackClient callbackClient;
    private final ApiExecProperties properties;
    private final ObjectMapper objectMapper;

    public ScenarioExecutionService(ScenarioTaskRepository tasks, ScenarioYamlParser parser,
                                    ScenarioExecutor executor, TaskQueue queue,
                                    CancellationStore cancellationStore, CoreCallbackClient callbackClient,
                                    ApiExecProperties properties, ObjectMapper objectMapper) {
        this.tasks = tasks;
        this.parser = parser;
        this.executor = executor;
        this.queue = queue;
        this.cancellationStore = cancellationStore;
        this.callbackClient = callbackClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ScenarioDtos.CreateScenarioResponse create(ScenarioDtos.CreateScenarioRequest request, String actor) {
        if (request == null || request.scenarioId() == null || request.scenarioId().isBlank()) {
            throw ApiException.badRequest("scenarioId 不能为空");
        }
        parser.parse(request.yaml());
        int timeout = request.timeoutSec() == null || request.timeoutSec() <= 0
                ? 120 : Math.min(request.timeoutSec(), 3600);
        String createdBy = request.createdBy() == null || request.createdBy().isBlank()
                ? actor : request.createdBy().trim();
        ScenarioTaskEntity task = new ScenarioTaskEntity(Ids.newId("scenario-task"),
                request.scenarioId().trim(), blank(request.runId()), request.name(),
                request.yaml(), json(request.environment(), "{}"), json(request.variables(), "{}"),
                timeout, Instant.now());
        task.setPlanId(blank(request.planId()));
        task.setPlanRunId(blank(request.planRunId()));
        task.setCaseId(blank(request.caseId()));
        task.setCreatedBy(createdBy);
        task = tasks.saveAndFlush(task);
        try {
            queue.enqueue(QUEUE_PREFIX + task.getId());
        } catch (RuntimeException e) {
            log.warn("场景任务入队失败 taskId={}: {}", task.getId(), e.getMessage());
            task.setStatus(ApiTaskStatus.FAILED);
            task.setFailureReason("QUEUE_UNAVAILABLE");
            task.setSummary("queue unavailable: " + e.getMessage());
            task.setFinishedAt(Instant.now());
            tasks.save(task);
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "QUEUE_UNAVAILABLE", "任务队列不可用，请稍后重试");
        }
        log.info("场景任务已创建 taskId={} scenarioId={}", task.getId(), task.getScenarioId());
        return new ScenarioDtos.CreateScenarioResponse(task.getId(), task.getStatus().name());
    }

    /** Executes a claimed scenario task on the worker thread. */
    public void execute(String taskId, String workerId) {
        Instant startedAt = Instant.now();
        if (tasks.claim(taskId, ApiTaskStatus.RUNNING, ApiTaskStatus.PENDING, workerId, startedAt) == 0) {
            return;
        }
        ScenarioTaskEntity task = tasks.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        try {
            Map<String, Object> environment = readMap(task.getEnvironmentJson());
            Map<String, Object> variables = readMap(task.getVariablesJson());
            ScenarioRunResult result = executor.execute(task.getYamlText(), environment, variables,
                    task.getTimeoutSec(), () -> isCancelled(taskId));
            complete(task, result);
        } catch (RuntimeException e) {
            log.error("场景执行异常 taskId={}", taskId, e);
            finish(taskId, ApiTaskStatus.FAILED, "EXECUTOR_ERROR",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            try {
                cancellationStore.clear(taskId);
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
    }

    private void complete(ScenarioTaskEntity task, ScenarioRunResult result) {
        ApiTaskStatus status = switch (result.status()) {
            case "SUCCESS" -> ApiTaskStatus.SUCCESS;
            case "TIMEOUT" -> ApiTaskStatus.TIMEOUT;
            case "CANCELLED" -> ApiTaskStatus.CANCELLED;
            default -> ApiTaskStatus.FAILED;
        };
        boolean won = tasks.transition(task.getId(), ApiTaskStatus.RUNNING, status,
                result.failureReason(), result.summary(), Instant.now()) > 0;
        if (!won) {
            return;
        }
        ScenarioTaskEntity terminal = tasks.findById(task.getId()).orElse(null);
        if (terminal == null) {
            return;
        }
        terminal.setDurationMs(result.durationMs());
        terminal.setStepsJson(json(result.steps().stream().map(ScenarioStepResult::toMap).toList(), "[]"));
        terminal.setReportJson(report(terminal, result));
        terminal.setSummary(result.summary());
        terminal.setFailureReason(result.failureReason());
        terminal.setFinishedAt(terminal.getFinishedAt() == null ? Instant.now() : terminal.getFinishedAt());
        tasks.save(terminal);
        log.info("场景任务完成 taskId={} status={} steps={} durationMs={}", terminal.getId(), status,
                result.steps().size(), result.durationMs());
        notifyCore(terminal);
    }

    public ScenarioDtos.ScenarioTaskView detail(String taskId) {
        ScenarioTaskEntity task = require(taskId);
        return new ScenarioDtos.ScenarioTaskView(task.getId(), task.getScenarioId(), task.getRunId(),
                task.getScenarioName(), task.getStatus().name(), task.getWorkerId(), task.getSummary(),
                task.getFailureReason(), task.getDurationMs(), readJson(task.getStepsJson()),
                readJson(task.getReportJson()), task.getCreatedBy(), task.getCreatedAt(),
                task.getStartedAt(), task.getFinishedAt());
    }

    public JsonNode report(String taskId) {
        ScenarioTaskEntity task = require(taskId);
        JsonNode node = readJson(task.getReportJson());
        return node == null ? objectMapper.createObjectNode() : node;
    }

    public ScenarioDtos.CancelScenarioResponse cancel(String taskId) {
        ScenarioTaskEntity before = require(taskId);
        if (before.getStatus().isTerminal()) {
            return new ScenarioDtos.CancelScenarioResponse(before.getId(), before.getStatus().name());
        }
        try {
            queue.remove(QUEUE_PREFIX + taskId);
            cancellationStore.requestCancel(taskId);
        } catch (RuntimeException e) {
            log.warn("场景取消标记失败 taskId={}: {}", taskId, e.getMessage());
        }
        int updated = tasks.cancelActive(taskId, ApiTaskStatus.CANCELLED, ApiTaskStatus.PENDING,
                ApiTaskStatus.RUNNING, Instant.now(), Instant.now());
        ScenarioTaskEntity current = require(taskId);
        if (updated > 0) {
            current.setSummary("cancelled");
            current.setReportJson(report(current, new ScenarioRunResult("CANCELLED", "CANCELLED",
                    "cancelled", null, List.of(), Map.of(), 0)));
            tasks.save(current);
        }
        return new ScenarioDtos.CancelScenarioResponse(current.getId(), current.getStatus().name());
    }

    public ScenarioDtos.ValidateResponse validate(String yaml) {
        ScenarioDefinition definition = parser.parse(yaml);
        List<String> types = definition.steps().stream()
                .map(step -> String.valueOf(step.get("type"))).toList();
        return new ScenarioDtos.ValidateResponse(true, "valid", definition.steps().size(), types);
    }

    public int recoverInterrupted() {
        int recovered = 0;
        for (ScenarioTaskEntity task : tasks.findByStatus(ApiTaskStatus.RUNNING)) {
            if (tasks.transition(task.getId(), ApiTaskStatus.RUNNING, ApiTaskStatus.FAILED,
                    "INTERRUPTED", "service restarted before scenario finished", Instant.now()) > 0) {
                recovered++;
            }
        }
        for (ScenarioTaskEntity task : tasks.findByStatus(ApiTaskStatus.PENDING)) {
            try {
                queue.enqueue(QUEUE_PREFIX + task.getId());
            } catch (RuntimeException e) {
                log.warn("恢复场景任务入队失败 taskId={}: {}", task.getId(), e.getMessage());
            }
        }
        return recovered;
    }

    public void failUnexpected(String taskId, Throwable error) {
        finish(taskId, ApiTaskStatus.FAILED, "EXECUTOR_ERROR",
                error == null || error.getMessage() == null ? "scenario executor error" : error.getMessage());
    }

    private void finish(String taskId, ApiTaskStatus status, String reason, String summary) {
        if (tasks.transition(taskId, ApiTaskStatus.RUNNING, status, reason, summary, Instant.now()) == 0) {
            return;
        }
        ScenarioTaskEntity task = tasks.findById(taskId).orElse(null);
        if (task != null) {
            task.setSummary(summary);
            task.setFailureReason(reason);
            task.setFinishedAt(task.getFinishedAt() == null ? Instant.now() : task.getFinishedAt());
            task.setReportJson(report(task, new ScenarioRunResult(status.name(), reason, summary, summary,
                    List.of(), Map.of(), task.getDurationMs() == null ? 0 : task.getDurationMs())));
            tasks.save(task);
            notifyCore(task);
        }
    }

    private boolean isCancelled(String taskId) {
        try {
            return cancellationStore.isCancelled(taskId);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void notifyCore(ScenarioTaskEntity task) {
        if (task == null || task.getPlanRunId() == null || task.getPlanRunId().isBlank()) {
            return;
        }
        try {
            String base = properties.getPublicBaseUrl();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            callbackClient.postPlanItemResult(new PlanItemResultPayload(task.getPlanRunId(), task.getCaseId(),
                    task.getId(), task.getStatus().name(), "API_SCENARIO", task.getFailureReason(),
                    base + "/api/v1/api-execution/scenarios/" + task.getId() + "/report", task.getSummary()));
        } catch (RuntimeException e) {
            log.warn("场景结果回调失败 taskId={}: {}", task.getId(), e.getMessage());
        }
    }

    private String report(ScenarioTaskEntity task, ScenarioRunResult result) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("taskId", task.getId());
        report.put("scenarioId", task.getScenarioId());
        report.put("runId", task.getRunId());
        report.put("name", task.getScenarioName());
        report.put("status", result.status());
        report.put("failureReason", result.failureReason());
        report.put("summary", result.summary());
        report.put("durationMs", result.durationMs());
        report.put("steps", result.steps().stream().map(ScenarioStepResult::toMap).toList());
        report.put("variables", result.variables());
        report.put("startedAt", task.getStartedAt() == null ? null : task.getStartedAt().toString());
        report.put("finishedAt", task.getFinishedAt() == null ? null : task.getFinishedAt().toString());
        return json(report, "{}");
    }

    private ScenarioTaskEntity require(String taskId) {
        return tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("场景任务不存在: " + taskId));
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String json(Object value, String fallback) {
        try {
            return value == null ? fallback : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
