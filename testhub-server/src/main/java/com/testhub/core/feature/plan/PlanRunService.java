package com.testhub.core.feature.plan;

import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.casemgmt.ClientCase;
import com.testhub.core.feature.casemgmt.ClientCaseRepository;
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

    public PlanRunService(TestPlanRepository planRepository, PlanCaseRepository planCaseRepository,
                          PlanRunRepository runRepository, PlanRunItemRepository itemRepository,
                          ClientCaseRepository caseRepository, ScriptService scriptService,
                          UiExecutionClient uiExecutionClient, AuditService auditService,
                          MeterSphereResultSink meterSphereResultSink) {
        this.planRepository = planRepository;
        this.planCaseRepository = planCaseRepository;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.caseRepository = caseRepository;
        this.scriptService = scriptService;
        this.uiExecutionClient = uiExecutionClient;
        this.auditService = auditService;
        this.meterSphereResultSink = meterSphereResultSink;
    }

    public TriggerResponse trigger(String planId, TriggerRequest request) {
        TestPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> ApiException.notFound("测试计划不存在: " + planId));
        List<PlanCase> mounts = planCaseRepository.findByPlanIdOrderByCreatedAtAscIdAsc(planId);
        if (mounts.isEmpty()) {
            throw noExecutableCase("测试计划未挂载任何用例");
        }
        List<ClientCase> cases = new ArrayList<>();
        long approved = 0;
        for (PlanCase mount : mounts) {
            ClientCase clientCase = caseRepository.findById(mount.getCaseId()).orElse(null);
            cases.add(clientCase);
            if (clientCase != null && clientCase.getReviewStatus() == ReviewStatus.APPROVED) {
                approved++;
            }
        }
        if (approved == 0) {
            throw noExecutableCase("测试计划没有已通过评审的用例");
        }

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

        List<TriggerItemView> views = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < mounts.size(); i++) {
            PlanCase mount = mounts.get(i);
            ClientCase clientCase = cases.get(i);
            Instant itemTime = startedAt.plusMillis(index++);
            if (clientCase == null) {
                PlanRunItem item = new PlanRunItem(Ids.newId("run-item"), run.getId(), mount.getCaseId(),
                        "(已删除用例)", mount.getExecutorTypeOverride(), RunItemStatus.SKIPPED, itemTime);
                item.setError("用例已不存在，已跳过");
                itemRepository.save(item);
                views.add(triggerView(item));
                continue;
            }
            ScriptProject script = scriptService.findOrNull(clientCase.getScriptId());
            ExecutorType intended = mount.getExecutorTypeOverride() != null
                    ? mount.getExecutorTypeOverride()
                    : (script != null ? ExecutorType.SCRIPT : ExecutorType.AI_RUNTIME);
            PlanRunItem item = new PlanRunItem(Ids.newId("run-item"), run.getId(), clientCase.getId(),
                    clientCase.getTitle(), intended, RunItemStatus.PENDING, itemTime);
            if (clientCase.getReviewStatus() != ReviewStatus.APPROVED) {
                item.setStatus(RunItemStatus.SKIPPED);
                item.setError("用例未通过评审，已跳过");
                item.setUpdatedAt(Instant.now());
                itemRepository.save(item);
                views.add(triggerView(item));
                continue;
            }
            // Persist the pending item before the remote call so an early callback can find it.
            itemRepository.save(item);
            try {
                Map<String, Object> payload = taskPayload(planId, run.getId(), clientCase,
                        mount.getExecutorTypeOverride(), options.targetDeviceId(), options.targetTags(),
                        timeoutSec, stepTimeoutSec, actor);
                UiExecutionClient.TaskCreated created = uiExecutionClient.createTask(payload);
                item.setTaskId(created.taskId());
                RunItemStatus status = RunItemStatus.parse(created.status());
                item.setStatus(status == null ? RunItemStatus.PENDING : status);
                ExecutorType actual = ExecutorType.parse(created.executorType());
                if (actual != null) {
                    item.setExecutorType(actual);
                }
                item.setDeviceId(created.deviceId());
            } catch (Exception e) {
                item.setStatus(RunItemStatus.FAILED);
                item.setError(truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
            item.setUpdatedAt(Instant.now());
            itemRepository.save(item);
            views.add(triggerView(item));
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

    /** Best-effort reconciliation against ui-execution; unreachable service is ignored. */
    public void reconcile(PlanRun run) {
        List<PlanRunItem> items = itemRepository.findByRunIdOrderByCreatedAtAscIdAsc(run.getId());
        boolean hasPending = items.stream()
                .anyMatch(item -> item.getStatus().isNonTerminal() && !isBlank(item.getTaskId()));
        if (!hasPending) {
            return;
        }
        List<UiExecutionClient.TaskSnapshot> snapshots;
        try {
            snapshots = uiExecutionClient.listTasksByPlanRun(run.getId());
        } catch (Exception e) {
            log.warn("ui-execution 对账失败（忽略）: {}", e.getMessage());
            return;
        }
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        Map<String, UiExecutionClient.TaskSnapshot> byTask = new LinkedHashMap<>();
        for (UiExecutionClient.TaskSnapshot snapshot : snapshots) {
            if (snapshot != null && !isBlank(snapshot.taskId())) {
                byTask.put(snapshot.taskId(), snapshot);
            }
        }
        for (PlanRunItem item : items) {
            if (isBlank(item.getTaskId()) || item.getStatus().isTerminal()) {
                continue;
            }
            UiExecutionClient.TaskSnapshot snapshot = byTask.get(item.getTaskId());
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

    private Map<String, Object> taskPayload(String planId, String runId, ClientCase clientCase,
                                           ExecutorType override, String targetDeviceId, List<String> targetTags,
                                           int timeoutSec, int stepTimeoutSec, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", planId);
        payload.put("planRunId", runId);
        payload.put("caseId", clientCase.getId());
        payload.put("executorType", override == null ? null : override.name());
        Map<String, Object> selector = new LinkedHashMap<>();
        selector.put("deviceId", targetDeviceId);
        selector.put("tags", targetTags == null ? List.of() : targetTags);
        payload.put("targetDeviceSelector", selector);
        payload.put("timeoutSec", timeoutSec);
        payload.put("stepTimeoutSec", stepTimeoutSec);
        payload.put("createdBy", actor);
        return payload;
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
