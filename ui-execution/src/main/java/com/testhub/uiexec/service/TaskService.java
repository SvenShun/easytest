package com.testhub.uiexec.service;

import com.testhub.common.security.SecurityProperties;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.uiexec.client.AgentClient;
import com.testhub.uiexec.client.AgentUnavailableException;
import com.testhub.uiexec.client.CoreClient;
import com.testhub.uiexec.client.CoreClientException;
import com.testhub.uiexec.client.dto.DispatchPayload;
import com.testhub.uiexec.client.dto.ExecutionContext;
import com.testhub.uiexec.client.dto.FailureAnalysisPayload;
import com.testhub.uiexec.client.dto.ModelCredential;
import com.testhub.uiexec.client.dto.PlanItemResultPayload;
import com.testhub.uiexec.config.UiExecProperties;
import com.testhub.uiexec.domain.AgentEntity;
import com.testhub.uiexec.domain.DeviceEntity;
import com.testhub.uiexec.domain.ExecutorType;
import com.testhub.uiexec.domain.FailureCategory;
import com.testhub.uiexec.domain.FailureReportEntity;
import com.testhub.uiexec.domain.SkillsPointerEntity;
import com.testhub.uiexec.domain.TaskEntity;
import com.testhub.uiexec.domain.TaskStateMachine;
import com.testhub.uiexec.domain.TaskStatus;
import com.testhub.uiexec.events.TaskEventPublisher;
import com.testhub.uiexec.queue.DeviceQueueStore;
import com.testhub.uiexec.repository.AgentRepository;
import com.testhub.uiexec.repository.TaskRepository;
import com.testhub.uiexec.routing.ExecutorRouter;
import com.testhub.uiexec.routing.RoutingDecision;
import com.testhub.uiexec.routing.RoutingInput;
import com.testhub.uiexec.web.dto.TaskDtos.BddView;
import com.testhub.uiexec.web.dto.TaskDtos.CreateTaskRequest;
import com.testhub.uiexec.web.dto.TaskDtos.FailureReportView;
import com.testhub.uiexec.web.dto.TaskDtos.LogLine;
import com.testhub.uiexec.web.dto.TaskDtos.LogView;
import com.testhub.uiexec.web.dto.TaskDtos.SkillsRefView;
import com.testhub.uiexec.web.dto.TaskDtos.StepView;
import com.testhub.uiexec.web.dto.TaskDtos.TaskDetailView;
import com.testhub.uiexec.web.dto.TaskDtos.TaskResultRequest;
import com.testhub.uiexec.web.dto.TaskDtos.TaskSummaryView;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core orchestration: create → route → select device → enqueue → dispatch,
 * agent result/cancel handling, timeout watchdog and read models.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final int DEFAULT_TIMEOUT_SEC = 120;
    private static final int DEFAULT_STEP_TIMEOUT_SEC = 30;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_LOGS = 500;
    private static final int LOST_AGENT_SECONDS = 120;
    private static final int WATCHDOG_GRACE_SECONDS = 15;
    private static final List<TaskStatus> ACTIVE_STATUSES = List.of(TaskStatus.DISPATCHED, TaskStatus.RUNNING);

    private final TaskRepository tasks;
    private final AgentRepository agents;
    private final DeviceService devices;
    private final DeviceQueueStore queue;
    private final CoreClient core;
    private final AgentClient agentClient;
    private final SkillsService skills;
    private final ExecutorRouter router;
    private final FailureClassifier classifier;
    private final FailureReportService failureReportService;
    private final ArtifactService artifactService;
    private final StepService stepService;
    private final LogService logService;
    private final ExecutionLock lock;
    private final UiExecProperties properties;
    private final SecurityProperties securityProperties;
    private final TaskEventPublisher eventPublisher;

    public TaskService(TaskRepository tasks, AgentRepository agents, DeviceService devices,
                       DeviceQueueStore queue, CoreClient core, AgentClient agentClient, SkillsService skills,
                       ExecutorRouter router, FailureClassifier classifier,
                       FailureReportService failureReportService, ArtifactService artifactService,
                       StepService stepService, LogService logService, ExecutionLock lock,
                       UiExecProperties properties, SecurityProperties securityProperties,
                       TaskEventPublisher eventPublisher) {
        this.tasks = tasks;
        this.agents = agents;
        this.devices = devices;
        this.queue = queue;
        this.core = core;
        this.agentClient = agentClient;
        this.skills = skills;
        this.router = router;
        this.classifier = classifier;
        this.failureReportService = failureReportService;
        this.artifactService = artifactService;
        this.stepService = stepService;
        this.logService = logService;
        this.lock = lock;
        this.properties = properties;
        this.securityProperties = securityProperties;
        this.eventPublisher = eventPublisher;
    }

    // ---------------------------------------------------------------- create

    public TaskEntity create(CreateTaskRequest request, String actor) {
        if (request == null || request.caseId() == null || request.caseId().isBlank()) {
            throw ApiException.badRequest("caseId 不能为空");
        }
        String caseId = request.caseId().trim();
        int timeoutSec = positiveOrDefault(request.timeoutSec(), DEFAULT_TIMEOUT_SEC);
        int stepTimeoutSec = positiveOrDefault(request.stepTimeoutSec(), DEFAULT_STEP_TIMEOUT_SEC);

        ExecutionContext context;
        try {
            context = core.fetchExecutionContext(caseId);
        } catch (CoreClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CORE_UNAVAILABLE", e.getMessage());
        }

        boolean hasScript = context.script() != null;
        ExecutorType requested = request.executorType();
        boolean needVision = requested == ExecutorType.AI_RUNTIME || (requested == null && !hasScript);
        boolean visionConfigured = false;
        if (needVision) {
            try {
                visionConfigured = core.fetchVisionCredential().isPresent();
            } catch (CoreClientException e) {
                log.warn("获取视觉模型配置失败，按未配置处理 caseId={}: {}", caseId, e.getMessage());
            }
        }

        RoutingDecision decision = router.route(RoutingInput.ofReviewStatus(
                context.reviewStatus(), hasScript, requested, visionConfigured));
        if (decision.rejected()) {
            throw new ApiException(HttpStatus.CONFLICT, decision.errorCode(), decision.message());
        }

        String createdBy = request.createdBy() == null || request.createdBy().isBlank() ? actor : request.createdBy();
        TaskEntity task = new TaskEntity(Ids.newId("task"), caseId, decision.executorType(), TaskStatus.PENDING,
                timeoutSec, stepTimeoutSec, Instant.now());
        task.setPlanId(blankToNull(request.planId()));
        task.setPlanRunId(blankToNull(request.planRunId()));
        task.setCaseTitle(context.title());
        if (context.bdd() != null) {
            task.setBddGiven(context.bdd().given());
            task.setBddWhen(context.bdd().when());
            task.setBddThen(context.bdd().then());
        }
        task.setCreatedBy(createdBy);
        task = tasks.save(task);

        if (decision.immediateFailure()) {
            log.warn("任务因执行器不可用而直接失败 caseId={} reason={}", caseId, decision.message());
            finishLocked(task, TaskStatus.FAILED, FailureCategory.EXECUTOR_UNAVAILABLE, decision.message());
            return requireTask(task.getId());
        }
        if (decision.hasFallback()) {
            log.info("执行器回退 caseId={} -> {} reason={}", caseId, decision.executorType(), decision.note());
            logService.append(task.getId(), List.of(new LogLine("WARN", decision.note())));
        }

        DeviceEntity device = devices.selectDevice(request.targetDeviceSelector());
        if (device == null) {
            log.warn("无可用设备，任务直接失败 caseId={}", caseId);
            finishLocked(task, TaskStatus.FAILED, FailureCategory.EXECUTOR_UNAVAILABLE, "无可用设备");
            return requireTask(task.getId());
        }

        task.setDeviceId(device.getId());
        task.setDeviceSerial(device.getSerial());
        task.setAgentId(device.getAgentId());
        queue.enqueue(device.getId(), task.getId());
        task.setQueuePosition(queue.position(device.getId(), task.getId()));
        task = tasks.save(task);
        log.info("任务已入队 taskId={} deviceId={} position={}", task.getId(), device.getId(),
                task.getQueuePosition());

        tryDispatch(device.getId());
        return requireTask(task.getId());
    }

    // -------------------------------------------------------------- dispatch

    /** Pops and dispatches queued tasks while the device stays idle. */
    public void tryDispatch(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        lock.lock();
        try {
            for (int guard = 0; guard < 100; guard++) {
                DeviceEntity device = devices.findDevice(deviceId).orElse(null);
                if (device == null || !devices.isDispatchable(device)) {
                    return;
                }
                if (devices.currentTask(deviceId) != null) {
                    return;
                }
                String nextTaskId = queue.popNext(deviceId);
                if (nextTaskId == null) {
                    return;
                }
                TaskEntity task = tasks.findById(nextTaskId).orElse(null);
                if (task == null) {
                    log.warn("队列中的任务不存在，已跳过 taskId={} deviceId={}", nextTaskId, deviceId);
                    continue;
                }
                if (task.getStatus() != TaskStatus.PENDING) {
                    continue;
                }
                dispatchOne(task, device);
            }
        } finally {
            lock.unlock();
        }
    }

    private void dispatchOne(TaskEntity task, DeviceEntity device) {
        ExecutionContext context;
        try {
            context = core.fetchExecutionContext(task.getCaseId());
        } catch (CoreClientException e) {
            finishLocked(task, TaskStatus.FAILED, FailureCategory.EXECUTOR_UNAVAILABLE,
                    "获取执行上下文失败: " + e.getMessage());
            return;
        }
        if (task.getExecutorType() == ExecutorType.SCRIPT && context.script() == null) {
            finishLocked(task, TaskStatus.FAILED, FailureCategory.EXECUTOR_UNAVAILABLE,
                    "用例脚本已不可用，无法使用 SCRIPT 执行器");
            return;
        }
        ModelCredential model = null;
        if (task.getExecutorType() == ExecutorType.AI_RUNTIME) {
            try {
                Optional<ModelCredential> credential = core.fetchVisionCredential();
                if (credential.isEmpty()) {
                    finishLocked(task, TaskStatus.FAILED, FailureCategory.EXECUTOR_UNAVAILABLE,
                            "视觉模型未配置，无法执行 AI_RUNTIME 任务");
                    return;
                }
                model = credential.get();
            } catch (CoreClientException e) {
                finishLocked(task, TaskStatus.FAILED, FailureCategory.EXECUTOR_UNAVAILABLE,
                        "获取视觉模型凭证失败: " + e.getMessage());
                return;
            }
        }
        AgentEntity agent = agents.findById(device.getAgentId()).orElse(null);
        if (agent == null) {
            finishLocked(task, TaskStatus.FAILED, FailureCategory.EXECUTOR_UNAVAILABLE, "设备所属 Agent 不存在");
            return;
        }

        // Skills pointer is read at dispatch time so queued tasks pick up the newest commit.
        SkillsPointerEntity pointer = skills.getOrCreate();
        task.setSkillsRepo(blankToNull(pointer.getGitUrl()));
        task.setSkillsCommit(blankToNull(pointer.getActiveCommit()));
        task.setAgentId(agent.getId());
        task.setStatus(TaskStatus.DISPATCHED);
        task.setDispatchedAt(Instant.now());
        task.setQueuePosition(0);
        tasks.save(task);

        DispatchPayload payload = buildPayload(task, device, context, model, pointer);
        try {
            agentClient.dispatch(agent.getBaseUrl(), payload);
            log.info("任务下发成功 taskId={} deviceId={} agentId={}", task.getId(), device.getId(), agent.getId());
            publishQuietly(() -> eventPublisher.dispatched(task, device), task);
        } catch (AgentUnavailableException e) {
            log.warn("任务下发失败 taskId={} agentId={}: {}", task.getId(), agent.getId(), e.getMessage());
            finishLocked(task, TaskStatus.FAILED, FailureCategory.EXECUTOR_UNAVAILABLE,
                    "任务下发失败: " + e.getMessage());
        }
    }

    private DispatchPayload buildPayload(TaskEntity task, DeviceEntity device, ExecutionContext context,
                                         ModelCredential model, SkillsPointerEntity pointer) {
        String base = trimTrailingSlash(properties.getCallbackBaseUrl());
        String taskBase = base + "/api/v1/ui-execution/tasks/" + task.getId();
        DispatchPayload.Callbacks callbacks = new DispatchPayload.Callbacks(
                taskBase + "/steps", taskBase + "/logs", taskBase + "/result",
                securityProperties.getAgentToken());
        DispatchPayload.Script script = context.script() == null ? null
                : new DispatchPayload.Script(context.script().id(), context.script().scriptType(),
                context.script().name(), context.script().entryCommand(), context.script().bundleUrl());
        DispatchPayload.Skills skillsRef = null;
        if (pointer != null && pointer.getActiveCommit() != null && !pointer.getActiveCommit().isBlank()) {
            skillsRef = new DispatchPayload.Skills(pointer.getGitUrl(), pointer.getActiveCommit());
        }
        DispatchPayload.Model modelRef = model == null ? null
                : new DispatchPayload.Model(model.provider(), model.endpoint(), model.apiKey(), model.modelId());
        DispatchPayload.Bdd bdd = context.bdd() == null ? null
                : new DispatchPayload.Bdd(context.bdd().given(), context.bdd().when(), context.bdd().then());
        return new DispatchPayload(
                task.getId(),
                task.getExecutorType(),
                task.getCaseId(),
                task.getCaseTitle(),
                bdd,
                script,
                new DispatchPayload.Device(device.getSerial(), device.getPlatform(),
                        DeviceService.splitTags(device.getTags())),
                skillsRef,
                new DispatchPayload.Timeouts(task.getTimeoutSec(), task.getStepTimeoutSec()),
                modelRef,
                callbacks);
    }

    // --------------------------------------------------------------- result

    public TaskEntity onResult(String taskId, TaskResultRequest request) {
        if (request == null || request.status() == null) {
            throw ApiException.badRequest("result.status 不能为空");
        }
        if (!request.status().isTerminal()) {
            throw ApiException.badRequest("result.status 必须是终止态");
        }
        lock.lock();
        try {
            TaskEntity task = requireTask(taskId);
            if (task.getStatus().isTerminal()) {
                throw new ApiException(HttpStatus.CONFLICT, "TASK_ALREADY_FINISHED", "任务已结束，重复的结果回调被忽略");
            }
            FailureCategory reported = parseReportedCategory(request.reasonCategory(), request.failureReason());
            String summary = request.summary();
            if ((summary == null || summary.isBlank())
                    && request.failureReason() != null
                    && FailureCategory.parse(request.failureReason()) == null) {
                summary = request.failureReason();
            }
            artifactService.storeResultArtifacts(taskId, request.artifacts());
            TaskEntity finished = finishLocked(task, request.status(), reported, summary);
            tryDispatch(task.getDeviceId());
            return finished;
        } finally {
            lock.unlock();
        }
    }

    // --------------------------------------------------------------- cancel

    public TaskEntity cancel(String taskId) {
        lock.lock();
        try {
            TaskEntity task = requireTask(taskId);
            if (task.getStatus().isTerminal()) {
                return task;
            }
            if (task.getStatus() == TaskStatus.PENDING) {
                if (task.getDeviceId() != null) {
                    queue.remove(task.getDeviceId(), task.getId());
                }
                finishLocked(task, TaskStatus.CANCELLED, FailureCategory.MANUAL_INTERRUPT, "任务在排队中被取消");
                tryDispatch(task.getDeviceId());
                return requireTask(taskId);
            }
            task.setCancelRequestedAt(Instant.now());
            task = tasks.save(task);
            AgentEntity agent = task.getAgentId() == null ? null
                    : agents.findById(task.getAgentId()).orElse(null);
            if (agent == null || agent.getBaseUrl() == null || agent.getBaseUrl().isBlank()) {
                finishLocked(task, TaskStatus.CANCELLED, FailureCategory.MANUAL_INTERRUPT,
                        "Agent 不可达，任务直接取消");
                tryDispatch(task.getDeviceId());
                return requireTask(taskId);
            }
            try {
                agentClient.cancel(agent.getBaseUrl(), taskId);
                log.info("已向 Agent 发送取消信号 taskId={} agentId={}", taskId, agent.getId());
                return task;
            } catch (AgentUnavailableException e) {
                log.warn("取消信号发送失败，任务直接取消 taskId={}: {}", taskId, e.getMessage());
                finishLocked(task, TaskStatus.CANCELLED, FailureCategory.MANUAL_INTERRUPT,
                        "Agent 不可达，任务直接取消");
                tryDispatch(task.getDeviceId());
                return requireTask(taskId);
            }
        } finally {
            lock.unlock();
        }
    }

    // -------------------------------------------------------------- watchdog

    public void checkTimeouts() {
        Instant now = Instant.now();
        for (TaskEntity candidate : tasks.findByStatusIn(ACTIVE_STATUSES)) {
            if (candidate.getDispatchedAt() == null) {
                continue;
            }
            Instant deadline = candidate.getDispatchedAt()
                    .plusSeconds(candidate.getTimeoutSec() + WATCHDOG_GRACE_SECONDS);
            if (!now.isAfter(deadline)) {
                continue;
            }
            lock.lock();
            try {
                TaskEntity task = tasks.findById(candidate.getId()).orElse(null);
                if (task == null || task.getStatus().isTerminal()) {
                    continue;
                }
                log.warn("任务超时 taskId={} timeoutSec={}", task.getId(), task.getTimeoutSec());
                finishLocked(task, TaskStatus.TIMEOUT, null,
                        "任务执行超时（" + task.getTimeoutSec() + " 秒）");
                tryDispatch(task.getDeviceId());
            } catch (RuntimeException e) {
                log.error("看门狗处理超时任务失败 taskId={}", candidate.getId(), e);
            } finally {
                lock.unlock();
            }
        }
    }

    public void checkLostAgents() {
        Instant threshold = Instant.now().minusSeconds(LOST_AGENT_SECONDS);
        for (TaskEntity candidate : tasks.findByStatusAndAgentIdIsNotNull(TaskStatus.RUNNING)) {
            AgentEntity agent = agents.findById(candidate.getAgentId()).orElse(null);
            if (agent != null && agent.getLastHeartbeat() != null && agent.getLastHeartbeat().isAfter(threshold)) {
                continue;
            }
            lock.lock();
            try {
                TaskEntity task = tasks.findById(candidate.getId()).orElse(null);
                if (task == null || task.getStatus() != TaskStatus.RUNNING) {
                    continue;
                }
                log.warn("Agent 心跳丢失，任务失败 taskId={} agentId={}", task.getId(), task.getAgentId());
                finishLocked(task, TaskStatus.FAILED, FailureCategory.EXECUTOR_UNAVAILABLE,
                        "Agent 心跳丢失超过 " + LOST_AGENT_SECONDS + " 秒");
                tryDispatch(task.getDeviceId());
            } catch (RuntimeException e) {
                log.error("看门狗处理心跳丢失任务失败 taskId={}", candidate.getId(), e);
            } finally {
                lock.unlock();
            }
        }
    }

    // ------------------------------------------------------- terminal update

    /**
     * Applies a terminal transition, generates the failure report, writes back
     * the case failure analysis and notifies the plan run. Idempotent: returns
     * the task unchanged when it is already terminal.
     */
    private TaskEntity finishLocked(TaskEntity task, TaskStatus status, FailureCategory reported, String summary) {
        TaskEntity current = tasks.findById(task.getId()).orElse(task);
        if (current.getStatus().isTerminal()) {
            return current;
        }
        TaskStateMachine.requireTransition(current.getStatus(), status);
        Instant now = Instant.now();
        current.setStatus(status);
        current.setFinishedAt(now);
        FailureCategory category = classifier.classify(status, current.getExecutorType(), reported);
        current.setFailureReason(category);
        String effectiveSummary = summary;
        if (status != TaskStatus.SUCCESS && (effectiveSummary == null || effectiveSummary.isBlank())) {
            effectiveSummary = defaultSummary(category);
        }
        current.setFailureSummary(truncate(effectiveSummary, 4000));

        if (status != TaskStatus.SUCCESS) {
            try {
                FailureReportEntity report = failureReportService.generate(current, category, effectiveSummary);
                current.setFailureReportUrl(report.getReportUrl());
            } catch (RuntimeException e) {
                // A storage outage must not leave the task stuck in a non-terminal state.
                log.error("生成失败轨迹报告失败 taskId={}: {}", current.getId(), e.getMessage(), e);
            }
        }
        current = tasks.save(current);

        if (status != TaskStatus.SUCCESS) {
            core.updateFailureAnalysis(current.getCaseId(), new FailureAnalysisPayload(
                    current.getId(),
                    current.getFailureReportUrl(),
                    effectiveSummary,
                    category == null ? null : category.name()));
        }
        core.postPlanItemResult(new PlanItemResultPayload(
                current.getPlanRunId(),
                current.getCaseId(),
                current.getId(),
                status.name(),
                current.getExecutorType() == null ? null : current.getExecutorType().name(),
                category == null ? null : category.name(),
                current.getFailureReportUrl(),
                effectiveSummary));
        TaskEntity finished = current;
        publishQuietly(() -> eventPublisher.finished(finished, null), finished);
        return finished;
    }

    private void publishQuietly(Runnable publish, TaskEntity task) {
        try {
            publish.run();
        } catch (RuntimeException e) {
            log.warn("task-event 发布失败 taskId={}: {}", task.getId(), e.getMessage());
        }
    }

    // ------------------------------------------------------------ read model

    public TaskEntity requireTask(String taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
    }

    public PageResponse<TaskSummaryView> search(String planRunId, String planId, TaskStatus status,
                                                String deviceId, String caseId, int page, int size) {
        int pageNumber = Math.max(1, page);
        int pageSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Specification<TaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(planRunId)) {
                predicates.add(cb.equal(root.get("planRunId"), planRunId.trim()));
            }
            if (hasText(planId)) {
                predicates.add(cb.equal(root.get("planId"), planId.trim()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (hasText(deviceId)) {
                predicates.add(cb.equal(root.get("deviceId"), deviceId.trim()));
            }
            if (hasText(caseId)) {
                predicates.add(cb.equal(root.get("caseId"), caseId.trim()));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<TaskEntity> result = tasks.findAll(spec, PageRequest.of(pageNumber - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result.getContent().stream().map(this::toSummary).toList(),
                result.getTotalElements(), pageNumber, pageSize);
    }

    public TaskDetailView detail(String taskId) {
        TaskEntity task = requireTask(taskId);
        BddView bdd = new BddView(task.getBddGiven(), task.getBddWhen(), task.getBddThen());
        SkillsRefView skillsRef = task.getSkillsRepo() == null && task.getSkillsCommit() == null
                ? null
                : new SkillsRefView(task.getSkillsRepo(), task.getSkillsCommit());
        FailureReportView failureReport = failureReportService.latest(taskId)
                .map(report -> new FailureReportView(report.getReportUrl(),
                        report.getReasonCategory() == null ? null : report.getReasonCategory().name(),
                        report.getSummary()))
                .orElse(null);
        return new TaskDetailView(
                task.getId(),
                task.getPlanId(),
                task.getPlanRunId(),
                task.getCaseId(),
                task.getCaseTitle(),
                bdd,
                task.getExecutorType(),
                task.getStatus(),
                task.getFailureReason(),
                task.getFailureSummary(),
                task.getFailureReportUrl(),
                queuePosition(task),
                task.getDeviceId(),
                task.getDeviceSerial(),
                task.getAgentId(),
                skillsRef,
                task.getTimeoutSec(),
                task.getStepTimeoutSec(),
                task.getCreatedAt(),
                task.getDispatchedAt(),
                task.getStartedAt(),
                task.getFinishedAt(),
                stepService.list(taskId),
                logService.recent(taskId, MAX_LOGS),
                artifactService.list(taskId),
                failureReport);
    }

    public TaskSummaryView toSummary(TaskEntity task) {
        return new TaskSummaryView(task.getId(), task.getPlanId(), task.getPlanRunId(), task.getCaseId(),
                task.getCaseTitle(), task.getExecutorType(), task.getStatus(), task.getFailureReason(),
                task.getFailureSummary(), task.getDeviceId(), task.getDeviceSerial(), task.getAgentId(),
                queuePosition(task), task.getCreatedAt(), task.getDispatchedAt(), task.getStartedAt(),
                task.getFinishedAt());
    }

    public List<StepView> listSteps(String taskId) {
        requireTask(taskId);
        return stepService.list(taskId);
    }

    public List<LogView> listLogs(String taskId, int afterSeq) {
        requireTask(taskId);
        if (afterSeq > 0) {
            return logService.afterSeq(taskId, afterSeq, MAX_LOGS);
        }
        return logService.recent(taskId, MAX_LOGS);
    }

    public int queuePosition(TaskEntity task) {
        if (task.getStatus() == TaskStatus.PENDING && task.getDeviceId() != null) {
            return queue.position(task.getDeviceId(), task.getId());
        }
        return 0;
    }

    private FailureCategory parseReportedCategory(String reasonCategory, String failureReason) {
        FailureCategory category = FailureCategory.parse(reasonCategory);
        if (category == null) {
            category = FailureCategory.parse(failureReason);
        }
        return category;
    }

    private String defaultSummary(FailureCategory category) {
        if (category == null) {
            return "任务执行失败";
        }
        return switch (category) {
            case TIMEOUT -> "任务执行超时";
            case MANUAL_INTERRUPT -> "任务被人工取消";
            case EXECUTOR_UNAVAILABLE -> "执行器不可用";
            case SKILLS_UNAVAILABLE -> "Skills 不可用";
            case SCRIPT_FAILED -> "脚本执行失败";
            case ELEMENT_NOT_FOUND -> "未找到目标元素";
            case MODEL_DECISION_FAILED -> "模型决策失败";
            case ACTION_FAILED -> "动作执行失败";
        };
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String truncate(String value, int max) {
        return value == null ? null : value.length() <= max ? value : value.substring(0, max);
    }
}
