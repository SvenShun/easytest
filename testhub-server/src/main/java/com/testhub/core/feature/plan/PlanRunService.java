package com.testhub.core.feature.plan;

import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.apicase.ApiCase;
import com.testhub.core.feature.apienv.ApiEnvironment;
import com.testhub.core.feature.apienv.ApiEnvironmentRepository;
import com.testhub.core.feature.apienv.ApiEnvironmentResolver;
import com.testhub.core.feature.apicase.ApiCaseRepository;
import com.testhub.core.feature.apicase.ApiCaseService;
import com.testhub.core.feature.casemgmt.ClientCase;
import com.testhub.core.feature.casemgmt.ClientCaseRepository;
import com.testhub.core.feature.casemgmt.ExecutionMode;
import com.testhub.core.feature.metersphere.MeterSphereResultSink;
import com.testhub.core.feature.casemgmt.ReviewStatus;
import com.testhub.core.feature.script.ScriptProject;
import com.testhub.core.feature.script.ScriptService;
import com.testhub.core.feature.support.AuditService;
import com.testhub.core.feature.support.PageSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plan triggering, run counters, task reconciliation and reports. */
@Service
public class PlanRunService {

    private static final Logger log = LoggerFactory.getLogger(PlanRunService.class);
    private static final int DEFAULT_TIMEOUT_SEC = 120;
    private static final int DEFAULT_STEP_TIMEOUT_SEC = 30;
    private static final int MAX_ERROR_LENGTH = 2000;

    private final TestPlanRepository planRepository;
    private final PlanCaseRepository planCaseRepository;
    private final PlanRunRepository runRepository;
    private final PlanRunItemRepository itemRepository;
    private final ClientCaseRepository caseRepository;
    private final ScriptService scriptService;
    private final UiExecutionClient uiExecutionClient;
    private final AuditService auditService;
    private final MeterSphereResultSink meterSphereResultSink;
    private final ApiExecutionClient apiExecutionClient;
    private final ApiCaseRepository apiCaseRepository;
    private final ApiCaseService apiCaseService;
    private final ApiEnvironmentRepository environmentRepository;
    private final ApiEnvironmentResolver environmentResolver;

    public PlanRunService(TestPlanRepository planRepository, PlanCaseRepository planCaseRepository,
                          PlanRunRepository runRepository, PlanRunItemRepository itemRepository,
                          ClientCaseRepository caseRepository, ScriptService scriptService,
                          UiExecutionClient uiExecutionClient, AuditService auditService,
                          MeterSphereResultSink meterSphereResultSink,
                          ApiExecutionClient apiExecutionClient, ApiCaseRepository apiCaseRepository,
                          ApiCaseService apiCaseService, ApiEnvironmentRepository environmentRepository,
                          ApiEnvironmentResolver environmentResolver) {
        this.planRepository = planRepository;
        this.planCaseRepository = planCaseRepository;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.caseRepository = caseRepository;
        this.scriptService = scriptService;
        this.uiExecutionClient = uiExecutionClient;
        this.auditService = auditService;
        this.meterSphereResultSink = meterSphereResultSink;
        this.apiExecutionClient = apiExecutionClient;
        this.apiCaseRepository = apiCaseRepository;
        this.apiCaseService = apiCaseService;
        this.environmentRepository = environmentRepository;
        this.environmentResolver = environmentResolver;
    }

    public TriggerResponse trigger(String planId, TriggerRequest request) {
        TestPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> ApiException.notFound("测试计划不存在: " + planId));
        List<PlanCase> mounts = planCaseRepository.findByPlanIdOrderByCreatedAtAscIdAsc(planId);
        if (mounts.isEmpty()) {
            throw noExecutableCase("测试计划未挂载任何用例");
        }
        long executable = mounts.stream().filter(mount -> isExecutable(mount)).count();
        if (executable == 0) {
            throw noExecutableCase("测试计划没有可执行的用例（UI 需已评审，API 需启用）");
        }
        ApiEnvironmentResolver.ResolvedEnvironment environment = resolveEnvironment(plan);

        TriggerRequest options = request == null
                ? new TriggerRequest(null, null, null, null) : request;
        int timeoutSec = positiveOrDefault(options.timeoutSec(), DEFAULT_TIMEOUT_SEC);
        int stepTimeoutSec = positiveOrDefault(options.stepTimeoutSec(), DEFAULT_STEP_TIMEOUT_SEC);
        String actor = currentActor();
        Instant startedAt = Instant.now();
        PlanRun run = runRepository.save(new PlanRun(Ids.newId("run"), planId, RunStatus.RUNNING, actor,
                startedAt));
        plan.setStatus("RUNNING");
        plan.setUpdatedAt(startedAt);
        planRepository.save(plan);

        List<String> targetDeviceIds = resolveTargetDeviceIds(options);
        List<TriggerItemView> views = new ArrayList<>();
        int index = 0;
        for (PlanCase mount : mounts) {
            Instant itemTime = startedAt.plusMillis(index++);
            if (mount.getCaseType() == CaseType.API) {
                ApiCase apiCase = apiCaseRepository.findById(mount.getCaseId()).orElse(null);
                if (apiCase == null || !apiCase.isEnabled()) {
                    PlanRunItem item = new PlanRunItem(Ids.newId("run-item"), run.getId(), mount.getCaseId(),
                            apiCase == null ? "(已删除接口用例)" : apiCase.getName(),
                            ExecutorType.API, RunItemStatus.SKIPPED, itemTime);
                    item.setError(apiCase == null ? "接口用例已不存在，已跳过" : "接口用例已禁用，已跳过");
                    item.setUpdatedAt(Instant.now());
                    itemRepository.save(item);
                    views.add(triggerView(item));
                    continue;
                }
                List<Map<String, Object>> rows = apiCaseService.dataset(apiCase);
                if (rows.isEmpty()) {
                    rows = List.of(Map.of());
                }
                for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                    Map<String, Object> row = rows.get(rowIndex);
                    String title = rows.size() > 1
                            ? apiCase.getName() + " [" + (rowIndex + 1) + "/" + rows.size() + "]"
                            : apiCase.getName();
                    PlanRunItem item = new PlanRunItem(Ids.newId("run-item"), run.getId(), mount.getCaseId(),
                            title, ExecutorType.API, RunItemStatus.PENDING, itemTime.plusMillis(rowIndex),
                            rows.size() > 1 ? rowIndex + 1 : 0);
                    itemRepository.save(item);
                    try {
                        int apiTimeout = Math.max(1, Math.min(timeoutSec, apiCase.getTimeoutSec()));
                        ApiExecutionClient.TaskCreated created = apiExecutionClient.createTask(
                                apiPayload(planId, run.getId(), apiCase, apiTimeout, actor, environment, row));
                        item.setTaskId(created.taskId());
                        RunItemStatus status = RunItemStatus.parse(created.status());
                        item.setStatus(status == null ? RunItemStatus.PENDING : status);
                    } catch (Exception e) {
                        item.setStatus(RunItemStatus.FAILED);
                        item.setError(truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                    }
                    item.setUpdatedAt(Instant.now());
                    itemRepository.save(item);
                    views.add(triggerView(item));
                }
                index += rows.size() - 1;
                continue;
            }

            ClientCase clientCase = caseRepository.findById(mount.getCaseId()).orElse(null);
            if (clientCase == null) {
                PlanRunItem item = new PlanRunItem(Ids.newId("run-item"), run.getId(), mount.getCaseId(),
                        "(已删除用例)", mount.getExecutorTypeOverride(), RunItemStatus.SKIPPED, itemTime);
                item.setError("用例已不存在，已跳过");
                itemRepository.save(item);
                views.add(triggerView(item));
                continue;
            }
            ScriptProject script = scriptService.findOrNull(clientCase.getScriptId());
            ExecutorType override = mount.getExecutorTypeOverride();
            boolean stepCase = clientCase.getExecutionMode() == ExecutionMode.STEPS;
            ExecutorType intended;
            String executorError = null;
            if (stepCase) {
                intended = ExecutorType.STEP;
                if (override != null && override != ExecutorType.STEP) {
                    intended = override;
                    executorError = "STEP 执行模式的用例只支持 STEP 执行器，当前覆盖为 " + override;
                } else if (clientCase.getSteps().stream().noneMatch(step -> step.isEnabled())) {
                    executorError = "STEP 执行模式的用例没有启用步骤，无法执行";
                }
            } else {
                intended = override != null
                        ? override
                        : (script != null ? ExecutorType.SCRIPT : ExecutorType.AI_RUNTIME);
                if (override == ExecutorType.STEP) {
                    executorError = "非 STEP 执行模式的用例不能使用 STEP 执行器";
                }
            }
            if (clientCase.getReviewStatus() != ReviewStatus.APPROVED) {
                PlanRunItem item = new PlanRunItem(Ids.newId("run-item"), run.getId(), clientCase.getId(),
                        clientCase.getTitle(), intended, RunItemStatus.SKIPPED, itemTime);
                item.setStatus(RunItemStatus.SKIPPED);
                item.setError("用例未通过评审，已跳过");
                item.setUpdatedAt(Instant.now());
                itemRepository.save(item);
                views.add(triggerView(item));
                continue;
            }
            if (executorError != null) {
                PlanRunItem item = new PlanRunItem(Ids.newId("run-item"), run.getId(), clientCase.getId(),
                        clientCase.getTitle(), intended, RunItemStatus.FAILED, itemTime);
                item.setStatus(RunItemStatus.FAILED);
                item.setError(executorError);
                item.setUpdatedAt(Instant.now());
                itemRepository.save(item);
                views.add(triggerView(item));
                continue;
            }
            int matrixIndex = 0;
            for (String targetDeviceId : targetDeviceIds) {
                String title = targetDeviceIds.size() > 1
                        ? clientCase.getTitle() + " [设备 " + (matrixIndex + 1) + "/" + targetDeviceIds.size() + "]"
                        : clientCase.getTitle();
                PlanRunItem item = new PlanRunItem(Ids.newId("run-item"), run.getId(), clientCase.getId(),
                        title, intended, RunItemStatus.PENDING, itemTime.plusMillis(index++));
                item.setDeviceId(targetDeviceId);
                // Persist the pending item before the remote call so an early callback can find it.
                itemRepository.save(item);
                try {
                    Map<String, Object> payload = taskPayload(planId, run.getId(), clientCase,
                            intended, targetDeviceId, options.targetTags(),
                            timeoutSec, stepTimeoutSec, actor);
                    UiExecutionClient.TaskCreated created = uiExecutionClient.createTask(payload);
                    item.setTaskId(created.taskId());
                    RunItemStatus status = RunItemStatus.parse(created.status());
                    item.setStatus(status == null ? RunItemStatus.PENDING : status);
                    ExecutorType actual = ExecutorType.parse(created.executorType());
                    if (actual != null) {
                        item.setExecutorType(actual);
                    }
                    if (created.deviceId() != null) {
                        item.setDeviceId(created.deviceId());
                    }
                } catch (Exception e) {
                    item.setStatus(RunItemStatus.FAILED);
                    item.setError(truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
                item.setUpdatedAt(Instant.now());
                itemRepository.save(item);
                views.add(triggerView(item));
                matrixIndex++;
            }
        }

        PlanRun finished = recompute(run);
        auditService.record(actor, AuditService.PLAN_TRIGGER, "plan:" + planId,
                "runId=" + finished.getId() + ",items=" + views.size());
        return new TriggerResponse(finished.getId(), views);
    }

    public PageResponse<PlanRunView> listRuns(String planId, Integer page, Integer size) {
        if (!planRepository.existsById(planId)) {
            throw ApiException.notFound("测试计划不存在: " + planId);
        }
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        return PageSupport.of(runRepository.findByPlanId(planId, pageRequest), PlanRunView::of);
    }

    public PlanRunDetailView getRun(String runId) {
        PlanRun run = requireRun(runId);
        reconcile(run);
        PlanRun refreshed = recompute(run);
        return new PlanRunDetailView(PlanRunView.of(refreshed), items(refreshed.getId()));
    }

    public PlanReportView report(String runId) {
        PlanRun run = requireRun(runId);
        reconcile(run);
        PlanRun refreshed = recompute(run);
        List<PlanRunItem> items = itemRepository.findByRunIdOrderByCreatedAtAscIdAsc(refreshed.getId());
        return new PlanReportView(PlanRunView.of(refreshed), RunSummary.of(refreshed),
                executorStats(items), failureCategories(items),
                items.stream().map(PlanRunItemView::of).toList());
    }

    public void applyResult(InternalResultRequest request) {
        if (request == null || isBlank(request.planRunId())) {
            throw ApiException.badRequest("planRunId 不能为空");
        }
        PlanRun run = requireRun(request.planRunId());
        RunItemStatus status = RunItemStatus.parse(request.status());
        if (status == null) {
            throw ApiException.badRequest("未知的任务状态: " + request.status());
        }
        PlanRunItem item;
        if (!isBlank(request.taskId())) {
            item = itemRepository.findByTaskId(request.taskId()).orElse(null);
        } else if (!isBlank(request.caseId())) {
            item = itemRepository.findByRunIdAndCaseId(run.getId(), request.caseId()).orElse(null);
        } else {
            item = null;
        }
        if (item == null || !run.getId().equals(item.getRunId())) {
            throw ApiException.notFound("计划运行项不存在");
        }
        item.setStatus(status);
        ExecutorType executorType = ExecutorType.parse(request.executorType());
        if (executorType != null) {
            item.setExecutorType(executorType);
        }
        if (request.failureReason() != null) {
            item.setFailureReason(request.failureReason());
        }
        if (request.failureReportUrl() != null) {
            item.setFailureReportUrl(request.failureReportUrl());
        }
        if (request.summary() != null) {
            item.setSummary(request.summary());
        }
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        writeMeterSphereResult(run, item);
        recompute(run);
    }

    /** Best-effort write-back into MeterSphere; never breaks the TestHub callback. */
    private void writeMeterSphereResult(PlanRun run, PlanRunItem item) {
        try {
            TestPlan plan = planRepository.findById(run.getPlanId()).orElse(null);
            PlanCase planCase = planCaseRepository.findByPlanIdAndCaseId(run.getPlanId(), item.getCaseId())
                    .orElse(null);
            meterSphereResultSink.write(plan, planCase, run, item);
        } catch (RuntimeException e) {
            log.warn("MeterSphere 结果回写异常（忽略）: {}", e.getMessage());
        }
    }

    /** Best-effort reconciliation against both execution services; unreachable services are ignored. */
    public void reconcile(PlanRun run) {
        List<PlanRunItem> items = itemRepository.findByRunIdOrderByCreatedAtAscIdAsc(run.getId());
        boolean hasPending = items.stream()
                .anyMatch(item -> item.getStatus().isNonTerminal() && !isBlank(item.getTaskId()));
        if (!hasPending) {
            return;
        }
        Map<String, Snapshot> byTask = new LinkedHashMap<>();
        boolean hasUi = items.stream().anyMatch(item -> !item.getStatus().isTerminal()
                && !isBlank(item.getTaskId()) && item.getExecutorType() != ExecutorType.API);
        boolean hasApi = items.stream().anyMatch(item -> !item.getStatus().isTerminal()
                && !isBlank(item.getTaskId()) && item.getExecutorType() == ExecutorType.API);
        try {
            if (!hasUi) {
                throw new IllegalStateException("no UI item");
            }
            for (UiExecutionClient.TaskSnapshot snapshot : uiExecutionClient.listTasksByPlanRun(run.getId())) {
                if (snapshot != null && !isBlank(snapshot.taskId())) {
                    byTask.put(snapshot.taskId(), new Snapshot(snapshot.taskId(), snapshot.status(),
                            snapshot.executorType(), snapshot.deviceId(), snapshot.failureReason(),
                            snapshot.failureReportUrl(), snapshot.summary()));
                }
            }
        } catch (Exception e) {
            if (hasUi) {
                log.warn("ui-execution 对账失败（忽略）: {}", e.getMessage());
            }
        }
        try {
            if (!hasApi) {
                throw new IllegalStateException("no API item");
            }
            for (ApiExecutionClient.TaskSnapshot snapshot : apiExecutionClient.listTasksByPlanRun(run.getId())) {
                if (snapshot != null && !isBlank(snapshot.taskId())) {
                    byTask.put(snapshot.taskId(), new Snapshot(snapshot.taskId(), snapshot.status(),
                            "API", null, snapshot.failureReason(), snapshot.failureReportUrl(),
                            snapshot.summary()));
                }
            }
        } catch (Exception e) {
            if (hasApi) {
                log.warn("api-execution 对账失败（忽略）: {}", e.getMessage());
            }
        }
        if (byTask.isEmpty()) {
            return;
        }
        for (PlanRunItem item : items) {
            if (isBlank(item.getTaskId()) || item.getStatus().isTerminal()) {
                continue;
            }
            Snapshot snapshot = byTask.get(item.getTaskId());
            if (snapshot == null) {
                continue;
            }
            RunItemStatus status = RunItemStatus.parse(snapshot.status());
            if (status == null) {
                continue;
            }
            item.setStatus(status);
            ExecutorType executorType = ExecutorType.parse(snapshot.executorType());
            if (executorType != null) {
                item.setExecutorType(executorType);
            }
            if (!isBlank(snapshot.deviceId())) {
                item.setDeviceId(snapshot.deviceId());
            }
            if (snapshot.failureReason() != null) {
                item.setFailureReason(snapshot.failureReason());
            }
            if (snapshot.failureReportUrl() != null) {
                item.setFailureReportUrl(snapshot.failureReportUrl());
            }
            if (snapshot.summary() != null) {
                item.setSummary(snapshot.summary());
            }
            item.setUpdatedAt(Instant.now());
            itemRepository.save(item);
        }
    }

    private record Snapshot(String taskId, String status, String executorType, String deviceId,
                            String failureReason, String failureReportUrl, String summary) {
    }

    /** Recomputes counters and derives the run status from its items. */
    public PlanRun recompute(PlanRun run) {
        List<PlanRunItem> items = itemRepository.findByRunIdOrderByCreatedAtAscIdAsc(run.getId());
        int success = 0;
        int failed = 0;
        int timeout = 0;
        int cancelled = 0;
        int running = 0;
        int pending = 0;
        int skipped = 0;
        for (PlanRunItem item : items) {
            switch (item.getStatus()) {
                case SUCCESS -> success++;
                case FAILED -> failed++;
                case TIMEOUT -> timeout++;
                case CANCELLED -> cancelled++;
                case DISPATCHED, RUNNING -> running++;
                case PENDING -> pending++;
                case SKIPPED -> skipped++;
            }
        }
        run.setTotal(items.size());
        run.setSuccess(success);
        run.setFailed(failed);
        run.setTimeout(timeout);
        run.setCancelled(cancelled);
        run.setRunning(running);
        run.setPending(pending);
        run.setSkipped(skipped);

        boolean nonTerminal = pending > 0 || running > 0;
        int failures = failed + timeout + cancelled;
        if (nonTerminal) {
            run.setStatus(RunStatus.RUNNING);
            run.setFinishedAt(null);
        } else if (failures == 0) {
            run.setStatus(RunStatus.COMPLETED);
            if (run.getFinishedAt() == null) {
                run.setFinishedAt(Instant.now());
            }
        } else if (success > 0) {
            run.setStatus(RunStatus.PARTIAL);
            if (run.getFinishedAt() == null) {
                run.setFinishedAt(Instant.now());
            }
        } else {
            run.setStatus(RunStatus.FAILED);
            if (run.getFinishedAt() == null) {
                run.setFinishedAt(Instant.now());
            }
        }
        PlanRun saved = runRepository.save(run);
        syncPlanStatus(saved.getPlanId());
        return saved;
    }

    public PlanRun requireRun(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> ApiException.notFound("计划运行不存在: " + runId));
    }

    private List<PlanRunItemView> items(String runId) {
        return itemRepository.findByRunIdOrderByCreatedAtAscIdAsc(runId).stream()
                .map(PlanRunItemView::of)
                .toList();
    }

    private TriggerItemView triggerView(PlanRunItem item) {
        return new TriggerItemView(item.getCaseId(), item.getCaseTitle(), item.getTaskId(), item.getStatus(),
                item.getExecutorType(), item.getError());
    }

    private boolean isExecutable(PlanCase mount) {
        if (mount.getCaseType() == CaseType.API) {
            return apiCaseRepository.findById(mount.getCaseId())
                    .map(ApiCase::isEnabled)
                    .orElse(false);
        }
        return caseRepository.findById(mount.getCaseId())
                .map(clientCase -> clientCase.getReviewStatus() == ReviewStatus.APPROVED)
                .orElse(false);
    }

    private ApiEnvironmentResolver.ResolvedEnvironment resolveEnvironment(TestPlan plan) {
        if (plan.getEnvironmentId() == null || plan.getEnvironmentId().isBlank()) {
            return new ApiEnvironmentResolver.ResolvedEnvironment("", Map.of(), Map.of());
        }
        ApiEnvironment environment = environmentRepository.findById(plan.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("环境不存在: " + plan.getEnvironmentId()));
        if (!environment.isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "ENV_DISABLED",
                    "测试计划引用的环境已禁用: " + environment.getName());
        }
        return environmentResolver.resolve(environment);
    }

    private Map<String, Object> apiPayload(String planId, String runId, ApiCase apiCase, int timeoutSec,
                                            String actor,
                                            ApiEnvironmentResolver.ResolvedEnvironment environment,
                                            Map<String, Object> dataRow) {
        var view = apiCaseService.view(apiCase);
        String baseUrl = isBlank(view.baseUrl()) ? environment.baseUrl() : view.baseUrl();
        baseUrl = environmentResolver.apply(baseUrl, environment, dataRow);
        String path = environmentResolver.apply(view.path(), environment, dataRow);
        if (isBlank(baseUrl) && !path.startsWith("http://") && !path.startsWith("https://")) {
            throw ApiException.badRequest("接口用例没有 baseUrl，且环境未配置 Base URL: " + apiCase.getName());
        }
        Map<String, String> headers = new LinkedHashMap<>(environment.headers());
        headers.putAll(environmentResolver.applyMap(view.headers(), environment, dataRow));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", view.method());
        request.put("baseUrl", baseUrl);
        request.put("path", path);
        request.put("headers", headers);
        request.put("query", environmentResolver.applyMap(view.query(), environment, dataRow));
        request.put("body", view.body() == null ? null : environmentResolver.apply(view.body(), environment, dataRow));
        request.put("timeoutSec", timeoutSec);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", planId);
        payload.put("planRunId", runId);
        payload.put("caseId", apiCase.getId());
        payload.put("caseName", view.name());
        payload.put("iteration", dataRow.isEmpty() ? 0 : 0);
        payload.put("request", request);
        payload.put("assertions", environmentResolver.applyAssertions(view.assertions(), environment, dataRow));
        payload.put("createdBy", actor);
        return payload;
    }

    private List<String> resolveTargetDeviceIds(TriggerRequest options) {
        List<String> ids = new ArrayList<>();
        if (options.targetDeviceIds() != null) {
            options.targetDeviceIds().stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .distinct()
                    .forEach(ids::add);
        }
        if (ids.isEmpty() && options.targetDeviceId() != null && !options.targetDeviceId().isBlank()) {
            ids.add(options.targetDeviceId().trim());
        }
        if (ids.isEmpty()) {
            ids.add(null);
        }
        return ids;
    }

    private Map<String, Object> taskPayload(String planId, String runId, ClientCase clientCase,
                                           ExecutorType executorType, String targetDeviceId, List<String> targetTags,
                                           int timeoutSec, int stepTimeoutSec, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", planId);
        payload.put("planRunId", runId);
        payload.put("caseId", clientCase.getId());
        payload.put("caseTitle", clientCase.getTitle());
        payload.put("executorType", executorType == null ? null : executorType.name());
        Map<String, Object> selector = new LinkedHashMap<>();
        selector.put("deviceId", targetDeviceId);
        selector.put("tags", targetTags == null ? List.of() : targetTags);
        payload.put("targetDeviceSelector", selector);
        payload.put("timeoutSec", timeoutSec);
        payload.put("stepTimeoutSec", stepTimeoutSec);
        payload.put("createdBy", actor);
        if (clientCase.getExecutionMode() == ExecutionMode.STEPS) {
            payload.put("steps", stepPayload(clientCase));
        }
        return payload;
    }

    private List<Map<String, Object>> stepPayload(ClientCase clientCase) {
        return clientCase.getSteps().stream()
                .filter(step -> step.isEnabled())
                .sorted((left, right) -> Integer.compare(left.getSeq(), right.getSeq()))
                .map(step -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("seq", step.getSeq());
                    row.put("stepType", step.getStepType() == null ? null : step.getStepType().name());
                    row.put("selectorType",
                            step.getSelectorType() == null ? null : step.getSelectorType().name());
                    row.put("selector", step.getSelector());
                    row.put("value", step.getValue());
                    row.put("assertValue", step.getAssertValue());
                    row.put("timeoutMs", step.getTimeoutMs());
                    return row;
                })
                .toList();
    }

    private List<ExecutorStats> executorStats(List<PlanRunItem> items) {
        Map<ExecutorType, int[]> stats = new EnumMap<>(ExecutorType.class);
        for (PlanRunItem item : items) {
            if (item.getExecutorType() == null) {
                continue;
            }
            int[] counters = stats.computeIfAbsent(item.getExecutorType(), key -> new int[3]);
            counters[0]++;
            if (item.getStatus() == RunItemStatus.SUCCESS) {
                counters[1]++;
            } else if (item.getStatus() == RunItemStatus.FAILED) {
                counters[2]++;
            }
        }
        List<ExecutorStats> result = new ArrayList<>();
        for (ExecutorType type : ExecutorType.values()) {
            int[] counters = stats.get(type);
            if (counters != null) {
                result.add(new ExecutorStats(type, counters[0], counters[1], counters[2]));
            }
        }
        return result;
    }

    private List<FailureCategory> failureCategories(List<PlanRunItem> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlanRunItem item : items) {
            if (item.getStatus().isFailure() && !isBlank(item.getFailureReason())) {
                counts.merge(item.getFailureReason(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new FailureCategory(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void syncPlanStatus(String planId) {
        planRepository.findById(planId).ifPresent(plan -> {
            boolean anyRunning = runRepository.findByPlanIdOrderByStartedAtDesc(planId).stream()
                    .anyMatch(run -> run.getStatus() == RunStatus.RUNNING);
            plan.setStatus(anyRunning ? "RUNNING" : "READY");
            plan.setUpdatedAt(Instant.now());
            planRepository.save(plan);
        });
    }

    private ApiException noExecutableCase(String message) {
        return new ApiException(HttpStatus.CONFLICT, "NO_EXECUTABLE_CASE", message);
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private String currentActor() {
        AuthPrincipal principal = CurrentUser.get();
        return principal == null ? "system" : principal.username();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
