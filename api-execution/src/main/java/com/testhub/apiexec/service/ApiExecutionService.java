package com.testhub.apiexec.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.apiexec.assertion.ApiAssertion;
import com.testhub.apiexec.assertion.AssertionEvaluator;
import com.testhub.apiexec.assertion.AssertionResult;
import com.testhub.apiexec.client.CoreCallbackClient;
import com.testhub.apiexec.client.PlanItemResultPayload;
import com.testhub.apiexec.config.ApiExecProperties;
import com.testhub.apiexec.config.ExecutorConfig;
import com.testhub.apiexec.domain.ApiTaskEntity;
import com.testhub.apiexec.domain.ApiTaskStatus;
import com.testhub.apiexec.execution.HttpExchangeResult;
import com.testhub.apiexec.execution.HttpExecutor;
import com.testhub.apiexec.execution.HttpRequestSpec;
import com.testhub.apiexec.execution.RequestUrlBuilder;
import com.testhub.apiexec.report.TaskReportFactory;
import com.testhub.apiexec.repository.ApiTaskRepository;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.CancelTaskResponse;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.CreateTaskRequest;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.CreateTaskResponse;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.RequestPayload;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.RequestView;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.ResponseView;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.TaskDetailView;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.TaskStatsView;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.TaskSummaryView;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Task lifecycle for api-execution: create → FIFO queue → HTTP execution →
 * assertion evaluation → terminal state → best-effort core callback, plus
 * cancellation, reporting and statistics.
 */
@Service
public class ApiExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ApiExecutionService.class);
    private static final int MAX_RESPONSE_CHARS = 4_000;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private final ApiTaskRepository tasks;
    private final ApiExecProperties properties;
    private final HttpExecutor httpExecutor;
    private final AssertionEvaluator assertionEvaluator;
    private final TaskReportFactory reportFactory;
    private final CoreCallbackClient callbackClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    /** Guards every state transition; also makes cancel-vs-finish races deterministic. */
    private final Object transitionLock = new Object();
    private final Map<String, CompletableFuture<HttpExchangeResult>> inFlight = new java.util.concurrent.ConcurrentHashMap<>();

    public ApiExecutionService(ApiTaskRepository tasks, ApiExecProperties properties,
                               HttpExecutor httpExecutor, AssertionEvaluator assertionEvaluator,
                               TaskReportFactory reportFactory, CoreCallbackClient callbackClient,
                               ObjectMapper objectMapper,
                               @Qualifier(ExecutorConfig.EXECUTOR_BEAN) ExecutorService executor) {
        this.tasks = tasks;
        this.properties = properties;
        this.httpExecutor = httpExecutor;
        this.assertionEvaluator = assertionEvaluator;
        this.reportFactory = reportFactory;
        this.callbackClient = callbackClient;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    // ---------------------------------------------------------------- create

    public CreateTaskResponse create(CreateTaskRequest request, String actor) {
        if (request == null || request.request() == null) {
            throw ApiException.badRequest("request 不能为空");
        }
        if (request.caseId() == null || request.caseId().isBlank()) {
            throw ApiException.badRequest("caseId 不能为空");
        }
        RequestPayload payload = request.request();
        String method = payload.method() == null ? "" : payload.method().trim().toUpperCase(Locale.ROOT);
        if (method.isEmpty()) {
            throw ApiException.badRequest("request.method 不能为空");
        }
        if (!ALLOWED_METHODS.contains(method)) {
            throw ApiException.badRequest("不支持的 HTTP 方法: " + payload.method());
        }
        Map<String, String> headers = cleanMap(payload.headers());
        Map<String, String> query = cleanMap(payload.query());
        String url = RequestUrlBuilder.build(payload.baseUrl(), payload.path(), query);
        int timeoutSec = payload.timeoutSec() == null || payload.timeoutSec() <= 0
                ? properties.getDefaultTimeoutSec() : payload.timeoutSec();
        List<ApiAssertion> assertions = request.assertions() == null
                ? List.of() : request.assertions().stream().filter(java.util.Objects::nonNull).toList();
        String createdBy = request.createdBy() == null || request.createdBy().isBlank()
                ? actor : request.createdBy().trim();

        Instant now = Instant.now();
        ApiTaskEntity task = new ApiTaskEntity(Ids.newId("api-task"), request.caseId().trim(), method, url,
                ApiTaskStatus.PENDING, Math.max(1, timeoutSec), now);
        task.setPlanId(blankToNull(request.planId()));
        task.setPlanRunId(blankToNull(request.planRunId()));
        task.setCaseName(request.caseName());
        task.setHeadersJson(toJson(headers, "{}"));
        task.setQueryJson(toJson(query, "{}"));
        task.setBody(payload.body());
        task.setAssertionsJson(toJson(assertions, "[]"));
        task.setCreatedBy(createdBy);
        task = tasks.save(task);
        log.info("接口执行任务已创建 taskId={} caseId={} planRunId={}", task.getId(), task.getCaseId(),
                task.getPlanRunId());

        final String taskId = task.getId();
        try {
            executor.execute(() -> execute(taskId));
        } catch (RejectedExecutionException e) {
            log.warn("执行器拒绝任务 taskId={}: {}", taskId, e.getMessage());
            markRejected(taskId, "executor rejected task: " + e.getMessage());
        }
        return new CreateTaskResponse(taskId, ApiTaskStatus.PENDING.name());
    }

    // --------------------------------------------------------------- execute

    /** Runs one queued task on a worker thread. */
    void execute(String taskId) {
        HttpRequestSpec spec;
        synchronized (transitionLock) {
            ApiTaskEntity task = tasks.findById(taskId).orElse(null);
            if (task == null || task.getStatus() != ApiTaskStatus.PENDING) {
                return;
            }
            task.setStatus(ApiTaskStatus.RUNNING);
            task.setStartedAt(Instant.now());
            tasks.save(task);
            spec = new HttpRequestSpec(task.getMethod(), task.getUrl(), readStringMap(task.getHeadersJson()),
                    task.getBody(), task.getTimeoutSec());
        }

        long startedAt = System.nanoTime();
        try {
            CompletableFuture<HttpExchangeResult> future = httpExecutor.executeAsync(spec);
            inFlight.put(taskId, future);
            if (isCancelRequested(taskId)) {
                future.cancel(true);
            }
            HttpExchangeResult result = future.get();
            complete(taskId, result);
        } catch (CancellationException e) {
            finishCancelledByWorker(taskId, startedAt);
        } catch (InterruptedException e) {
            // Executor shutdown (or an external interrupt) must not look like a user cancel:
            // leave the task RUNNING so startup recovery marks it FAILED (contract §1).
            Thread.currentThread().interrupt();
            log.warn("任务执行线程被中断，保留未完成状态等待启动恢复 taskId={}", taskId);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof HttpTimeoutException) {
                finishTerminal(taskId, ApiTaskStatus.TIMEOUT, "TIMEOUT",
                        "request timed out after " + spec.timeoutSec() + "s",
                        HttpExecutor.elapsedMillis(startedAt));
                return;
            }
            finishTerminal(taskId, ApiTaskStatus.FAILED, "HTTP_ERROR",
                    "request failed: " + describe(cause), HttpExecutor.elapsedMillis(startedAt));
        } catch (RuntimeException e) {
            finishTerminal(taskId, ApiTaskStatus.FAILED, "HTTP_ERROR",
                    "request failed: " + describe(e), HttpExecutor.elapsedMillis(startedAt));
        } finally {
            inFlight.remove(taskId);
        }
    }

    private void complete(String taskId, HttpExchangeResult result) {
        ApiTaskEntity terminal;
        synchronized (transitionLock) {
            ApiTaskEntity task = tasks.findById(taskId).orElse(null);
            if (task == null || task.getStatus() != ApiTaskStatus.RUNNING) {
                return;
            }
            List<ApiAssertion> assertions = readAssertions(task.getAssertionsJson());
            List<AssertionResult> results = assertionEvaluator.evaluate(assertions, result);
            boolean hasStatusAssertion = assertions.stream()
                    .anyMatch(a -> "STATUS_CODE".equalsIgnoreCase(trim(a.type())));
            long failures = results.stream().filter(r -> !r.passed()).count();

            ApiTaskStatus status;
            String failureReason;
            String summary;
            if (result.status() >= 400 && !hasStatusAssertion) {
                status = ApiTaskStatus.FAILED;
                failureReason = "HTTP_ERROR";
                summary = "HTTP " + result.status() + " without STATUS_CODE assertion, "
                        + result.durationMs() + "ms";
            } else if (failures > 0) {
                status = ApiTaskStatus.FAILED;
                failureReason = "ASSERTION_FAILED";
                summary = failures + "/" + results.size() + " assertions failed, HTTP "
                        + result.status() + ", " + result.durationMs() + "ms";
            } else {
                status = ApiTaskStatus.SUCCESS;
                failureReason = null;
                summary = results.isEmpty()
                        ? "no assertions, HTTP " + result.status() + ", " + result.durationMs() + "ms"
                        : results.size() + "/" + results.size() + " assertions passed, HTTP "
                        + result.status() + ", " + result.durationMs() + "ms";
            }

            task.setStatus(status);
            task.setFailureReason(failureReason);
            task.setSummary(summary);
            task.setHttpStatus(result.status());
            task.setDurationMs(result.durationMs());
            task.setResponseHeaders(toJson(result.headers(), "{}"));
            task.setResponseBody(truncate(result.body()));
            task.setAssertionResultsJson(toJson(results, "[]"));
            task.setFinishedAt(Instant.now());
            task.setReportJson(reportFactory.toJson(task));
            tasks.save(task);
            terminal = task;
            log.info("接口执行任务完成 taskId={} status={} httpStatus={} durationMs={}",
                    taskId, status, task.getHttpStatus(), task.getDurationMs());
        }
        notifyCore(terminal);
    }

    private void finishCancelledByWorker(String taskId, long startedAt) {
        finishTerminal(taskId, ApiTaskStatus.CANCELLED, "CANCELLED", "cancelled while running",
                HttpExecutor.elapsedMillis(startedAt));
    }

    /** A task rejected before it ever reached a worker is failed directly. */
    private void markRejected(String taskId, String message) {
        ApiTaskEntity terminal;
        synchronized (transitionLock) {
            ApiTaskEntity task = tasks.findById(taskId).orElse(null);
            if (task == null || task.getStatus() != ApiTaskStatus.PENDING) {
                return;
            }
            task.setStatus(ApiTaskStatus.FAILED);
            task.setFailureReason("HTTP_ERROR");
            task.setSummary(message);
            task.setFinishedAt(Instant.now());
            task.setAssertionResultsJson(toJson(List.of(), "[]"));
            task.setReportJson(reportFactory.toJson(task));
            tasks.save(task);
            terminal = task;
        }
        notifyCore(terminal);
    }

    /**
     * Applies a terminal state only when the task is still RUNNING, so a
     * user-initiated cancel always wins the race.
     */
    private void finishTerminal(String taskId, ApiTaskStatus status, String failureReason,
                                String summary, Long durationMs) {
        ApiTaskEntity terminal;
        synchronized (transitionLock) {
            ApiTaskEntity task = tasks.findById(taskId).orElse(null);
            if (task == null || task.getStatus() != ApiTaskStatus.RUNNING) {
                return;
            }
            task.setStatus(status);
            task.setFailureReason(failureReason);
            task.setSummary(summary);
            if (durationMs != null) {
                task.setDurationMs(durationMs);
            }
            task.setAssertionResultsJson(toJson(List.of(), "[]"));
            task.setFinishedAt(Instant.now());
            task.setReportJson(reportFactory.toJson(task));
            tasks.save(task);
            terminal = task;
            log.info("接口执行任务结束 taskId={} status={} reason={}", taskId, status, failureReason);
        }
        notifyCore(terminal);
    }

    // ------------------------------------------------------------------ reads

    public ApiTaskEntity requireTask(String taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("接口执行任务不存在: " + taskId));
    }

    public TaskDetailView detail(String taskId) {
        ApiTaskEntity task = requireTask(taskId);
        RequestView request = new RequestView(task.getMethod(), task.getUrl(),
                readStringMap(task.getHeadersJson()), readStringMap(task.getQueryJson()),
                task.getBody(), task.getTimeoutSec());
        ResponseView response = task.getHttpStatus() == null && task.getResponseBody() == null
                ? null
                : new ResponseView(task.getHttpStatus(), readHeaders(task.getResponseHeaders()),
                task.getResponseBody(), task.getDurationMs());
        return new TaskDetailView(task.getId(), task.getPlanId(), task.getPlanRunId(), task.getCaseId(),
                task.getCaseName(), name(task.getStatus()), task.getFailureReason(), task.getSummary(),
                request, response, reportFactory.readAssertionResults(task.getAssertionResultsJson()),
                task.getHttpStatus(), task.getDurationMs(), reportUrl(task.getId()), task.getCreatedBy(),
                task.getCreatedAt(), task.getStartedAt(), task.getFinishedAt());
    }

    public PageResponse<TaskSummaryView> search(String planRunId, ApiTaskStatus status, int page, int size) {
        int pageNumber = page <= 0 ? 1 : page;
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Specification<ApiTaskEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planRunId != null && !planRunId.isBlank()) {
                predicates.add(cb.equal(root.get("planRunId"), planRunId.trim()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ApiTaskEntity> result = tasks.findAll(specification, PageRequest.of(pageNumber - 1, pageSize,
                Sort.by(Sort.Direction.ASC, "createdAt", "id")));
        return PageResponse.of(result.getContent().stream().map(this::summary).toList(),
                result.getTotalElements(), pageNumber, pageSize);
    }

    public TaskStatsView stats() {
        return new TaskStatsView(
                tasks.countByStatus(ApiTaskStatus.PENDING),
                tasks.countByStatus(ApiTaskStatus.RUNNING),
                tasks.countByStatus(ApiTaskStatus.SUCCESS),
                tasks.countByStatus(ApiTaskStatus.FAILED),
                tasks.countByStatus(ApiTaskStatus.TIMEOUT),
                tasks.countByStatus(ApiTaskStatus.CANCELLED),
                Math.max(1, properties.getWorkers()));
    }

    public JsonNode report(String taskId) {
        ApiTaskEntity task = requireTask(taskId);
        if (task.getReportJson() != null && !task.getReportJson().isBlank()) {
            try {
                return objectMapper.readTree(task.getReportJson());
            } catch (Exception e) {
                log.warn("解析任务报告失败 taskId={}: {}", taskId, e.getMessage());
            }
        }
        return objectMapper.valueToTree(reportFactory.build(task));
    }

    // ----------------------------------------------------------------- cancel

    public CancelTaskResponse cancel(String taskId) {
        ApiTaskEntity terminal;
        CompletableFuture<HttpExchangeResult> future;
        synchronized (transitionLock) {
            ApiTaskEntity task = requireTask(taskId);
            if (task.getStatus().isTerminal()) {
                return new CancelTaskResponse(task.getId(), task.getStatus().name());
            }
            ApiTaskStatus previous = task.getStatus();
            Instant now = Instant.now();
            task.setCancelRequestedAt(now);
            task.setStatus(ApiTaskStatus.CANCELLED);
            task.setFailureReason("CANCELLED");
            task.setSummary(previous == ApiTaskStatus.PENDING
                    ? "cancelled before execution" : "cancelled while running");
            task.setFinishedAt(now);
            if (task.getStartedAt() != null) {
                task.setDurationMs(Math.max(0L,
                        now.toEpochMilli() - task.getStartedAt().toEpochMilli()));
            }
            task.setAssertionResultsJson(toJson(List.of(), "[]"));
            task.setReportJson(reportFactory.toJson(task));
            tasks.save(task);
            terminal = task;
            future = inFlight.get(taskId);
            log.info("接口执行任务已取消 taskId={} previous={}", taskId, previous);
        }
        if (future != null) {
            future.cancel(true);
        }
        notifyCore(terminal);
        return new CancelTaskResponse(terminal.getId(), terminal.getStatus().name());
    }

    // -------------------------------------------------------------- recovery

    /** Contract §1: unfinished tasks after a restart are marked FAILED. */
    public int recoverInterrupted() {
        List<ApiTaskEntity> interrupted = tasks.findByStatusIn(
                List.of(ApiTaskStatus.PENDING, ApiTaskStatus.RUNNING));
        int recovered = 0;
        for (ApiTaskEntity task : interrupted) {
            synchronized (transitionLock) {
                ApiTaskEntity current = tasks.findById(task.getId()).orElse(null);
                if (current == null || !current.getStatus().isActive()) {
                    continue;
                }
                current.setStatus(ApiTaskStatus.FAILED);
                current.setFailureReason("INTERRUPTED");
                current.setSummary("service restarted before task finished");
                current.setFinishedAt(Instant.now());
                current.setAssertionResultsJson(toJson(List.of(), "[]"));
                current.setReportJson(reportFactory.toJson(current));
                tasks.save(current);
                recovered++;
            }
        }
        if (recovered > 0) {
            log.warn("启动时标记 {} 个未完成接口执行任务为 FAILED", recovered);
        }
        return recovered;
    }

    // --------------------------------------------------------------- helpers

    private void notifyCore(ApiTaskEntity task) {
        if (task == null) {
            return;
        }
        try {
            callbackClient.postPlanItemResult(PlanItemResultPayload.from(task, properties.getPublicBaseUrl()));
        } catch (RuntimeException e) {
            log.warn("计划执行结果回调异常 taskId={}: {}", task.getId(), e.getMessage());
        }
    }

    private boolean isCancelRequested(String taskId) {
        return tasks.findById(taskId)
                .map(task -> task.getCancelRequestedAt() != null || task.getStatus() == ApiTaskStatus.CANCELLED)
                .orElse(false);
    }

    private TaskSummaryView summary(ApiTaskEntity task) {
        return new TaskSummaryView(task.getId(), task.getPlanId(), task.getPlanRunId(), task.getCaseId(),
                task.getCaseName(), task.getMethod(), task.getUrl(), name(task.getStatus()),
                task.getHttpStatus(), task.getDurationMs(), task.getFailureReason(), task.getSummary(),
                reportUrl(task.getId()), task.getCreatedBy(), task.getCreatedAt(), task.getStartedAt(),
                task.getFinishedAt());
    }

    private String reportUrl(String taskId) {
        return properties.failureReportUrl(taskId);
    }

    private List<ApiAssertion> readAssertions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ApiAssertion>>() {
            });
        } catch (Exception e) {
            log.warn("解析断言配置失败: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, List<String>> readHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, List<String>>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, String> cleanMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                cleaned.put(key.trim(), value);
            }
        });
        return cleaned;
    }

    private String toJson(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return fallback;
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_RESPONSE_CHARS ? value : value.substring(0, MAX_RESPONSE_CHARS);
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        String value = throwable.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String name(ApiTaskStatus status) {
        return status == null ? null : status.name();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
