package com.testhub.core.feature.job;

import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.job.JobDtos.JobRequest;
import com.testhub.core.feature.job.JobDtos.JobView;
import com.testhub.core.feature.apiscenario.ApiScenarioDtos;
import com.testhub.core.feature.apiscenario.ApiScenarioService;
import com.testhub.core.feature.plan.PlanRunService;
import com.testhub.core.feature.plan.TestPlan;
import com.testhub.core.feature.plan.TestPlanRepository;
import com.testhub.core.feature.plan.TriggerRequest;
import com.testhub.core.feature.plan.TriggerResponse;
import com.testhub.core.feature.support.PageSupport;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cron-style scheduled plan execution (Sonic Jobs parity). */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final TestJobRepository jobs;
    private final TestPlanRepository plans;
    private final PlanRunService planRunService;
    private final ApiScenarioService apiScenarioService;
    private final Map<String, Object> running = new ConcurrentHashMap<>();

    public JobService(TestJobRepository jobs, TestPlanRepository plans, PlanRunService planRunService) {
        this(jobs, plans, planRunService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JobService(TestJobRepository jobs, TestPlanRepository plans, PlanRunService planRunService,
                      ApiScenarioService apiScenarioService) {
        this.jobs = jobs;
        this.plans = plans;
        this.planRunService = planRunService;
        this.apiScenarioService = apiScenarioService;
    }

    public PageResponse<JobView> list(String keyword, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<TestJob> result = jobs.findAll(keywordSpec(keyword), pageRequest);
        return PageSupport.of(result, this::view);
    }

    public JobView get(String id) {
        return view(require(id));
    }

    @Transactional
    public JobView create(JobRequest request) {
        validate(request);
        AuthPrincipal principal = CurrentUser.require();
        Instant now = Instant.now();
        TestJob job = new TestJob(Ids.newId("job"), request.name().trim(), request.planId(),
                request.cronExpression().trim(), blankToNull(request.deviceId()),
                blankToNull(request.deviceTags()), join(request.deviceIds()), request.enabled() == null || request.enabled(),
                principal.username(), now, now);
        job.setScenarioId(blankToNull(request.scenarioId()));
        job.setNextRunAt(next(request.cronExpression(), now));
        return view(jobs.save(job));
    }

    @Transactional
    public JobView update(String id, JobRequest request) {
        validate(request);
        TestJob job = require(id);
        job.setName(request.name().trim());
        job.setPlanId(request.planId());
        job.setCronExpression(request.cronExpression().trim());
        job.setDeviceId(blankToNull(request.deviceId()));
        job.setDeviceTags(blankToNull(request.deviceTags()));
        job.setDeviceIds(join(request.deviceIds()));
        job.setScenarioId(blankToNull(request.scenarioId()));
        job.setEnabled(request.enabled() == null || request.enabled());
        job.setUpdatedAt(Instant.now());
        job.setNextRunAt(next(job.getCronExpression(), Instant.now()));
        return view(jobs.save(job));
    }

    @Transactional
    public JobView toggle(String id, boolean enabled) {
        TestJob job = require(id);
        job.setEnabled(enabled);
        job.setUpdatedAt(Instant.now());
        job.setNextRunAt(enabled ? next(job.getCronExpression(), Instant.now()) : null);
        return view(jobs.save(job));
    }

    @Transactional
    public void delete(String id) {
        jobs.delete(require(id));
    }

    /** Trigger immediately, regardless of schedule. */
    public JobView runNow(String id) {
        TestJob job = require(id);
        TriggerResponse run = trigger(job);
        job.setLastRunId(run.runId());
        job.setLastRunAt(Instant.now());
        job.setNextRunAt(next(job.getCronExpression(), Instant.now()));
        job.setUpdatedAt(Instant.now());
        return view(jobs.save(job));
    }

    @Scheduled(fixedDelay = 15_000)
    public void tick() {
        Instant now = Instant.now();
        for (TestJob job : jobs.findByEnabledTrueAndNextRunAtLessThanEqual(now)) {
            if (running.putIfAbsent(job.getId(), Boolean.TRUE) != null) {
                continue;
            }
            try {
                TriggerResponse run = trigger(job);
                job.setLastRunId(run.runId());
                job.setLastRunAt(now);
                job.setNextRunAt(next(job.getCronExpression(), now));
                job.setUpdatedAt(now);
                jobs.save(job);
                log.info("scheduled job {} triggered target {} run {}", job.getId(),
                        job.getScenarioId() != null ? job.getScenarioId() : job.getPlanId(), run.runId());
            } catch (RuntimeException e) {
                log.warn("scheduled job {} failed: {}", job.getId(), e.getMessage());
                job.setNextRunAt(next(job.getCronExpression(), now));
                jobs.save(job);
            } finally {
                running.remove(job.getId());
            }
        }
    }

    private TriggerResponse trigger(TestJob job) {
        if (job.getScenarioId() != null && !job.getScenarioId().isBlank()) {
            if (apiScenarioService == null) {
                throw ApiException.internal("场景调度器未配置");
            }
            ApiScenarioService.RunHandle handle = apiScenarioService.startScheduled(
                    job.getScenarioId(), job.getCreatedBy() == null ? "scheduler" : job.getCreatedBy());
            return new TriggerResponse(handle.runId(), List.of());
        }
        TestPlan plan = plans.findById(job.getPlanId())
                .orElseThrow(() -> ApiException.notFound("计划不存在: " + job.getPlanId()));
        List<String> tags = job.getDeviceTags() == null || job.getDeviceTags().isBlank()
                ? List.of()
                : Arrays.stream(job.getDeviceTags().split(",")).map(String::trim)
                        .filter(value -> !value.isEmpty()).toList();
        List<String> deviceIds = split(job.getDeviceIds());
        return planRunService.trigger(plan.getId(),
                new TriggerRequest(job.getDeviceId(), tags, 120, 30, deviceIds));
    }

    private void validate(JobRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("任务名称不能为空");
        }
        boolean hasPlan = request.planId() != null && !request.planId().isBlank();
        boolean hasScenario = request.scenarioId() != null && !request.scenarioId().isBlank();
        if (!hasPlan && !hasScenario) {
            throw ApiException.badRequest("必须指定测试计划或 API 场景");
        }
        if (hasPlan && !plans.existsById(request.planId())) {
            throw ApiException.notFound("测试计划不存在: " + request.planId());
        }
        if (hasScenario) {
            if (apiScenarioService == null) {
                throw ApiException.internal("场景调度器未配置");
            }
            if (!apiScenarioService.get(request.scenarioId()).enabled()) {
                throw ApiException.conflict("API 场景已禁用: " + request.scenarioId());
            }
        }
        if (request.cronExpression() == null || !CronExpression.isValidExpression(request.cronExpression().trim())) {
            throw ApiException.badRequest("Cron 表达式无效（Spring 6 段格式，如 0 0 2 * * *）");
        }
    }

    private Instant next(String cron, Instant from) {
        ZonedDateTime next = CronExpression.parse(cron).next(from.atZone(ZoneId.systemDefault()));
        return next == null ? null : next.toInstant();
    }

    private TestJob require(String id) {
        return jobs.findById(id).orElseThrow(() -> ApiException.notFound("任务不存在: " + id));
    }

    private JobView view(TestJob job) {
        String planName = job.getPlanId() == null ? null
                : plans.findById(job.getPlanId()).map(TestPlan::getName).orElse("(已删除)");
        String scenarioName = null;
        if (job.getScenarioId() != null) {
            try {
                ApiScenarioDtos.ScenarioView scenario = apiScenarioService.get(job.getScenarioId());
                scenarioName = scenario.name();
            } catch (RuntimeException e) {
                scenarioName = "(已删除)";
            }
        }
        return new JobView(job.getId(), job.getName(), job.getPlanId(), planName,
                job.getScenarioId(), scenarioName, job.getCronExpression(),
                job.getDeviceId(), job.getDeviceTags(), split(job.getDeviceIds()), job.isEnabled(), job.getLastRunId(),
                job.getLastRunAt(), job.getNextRunAt(), job.getCreatedBy(), job.getCreatedAt(),
                job.getUpdatedAt());
    }

    private Specification<TestJob> keywordSpec(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            predicates.add(cb.like(cb.lower(root.get("cronExpression")), pattern));
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).distinct().reduce((a, b) -> a + "," + b).orElse("");
        return joined.isBlank() ? null : joined;
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim)
                .filter(item -> !item.isEmpty()).distinct().toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
