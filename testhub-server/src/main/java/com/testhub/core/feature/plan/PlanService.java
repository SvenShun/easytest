package com.testhub.core.feature.plan;

import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.apicase.ApiCase;
import com.testhub.core.feature.apiscenario.ApiScenario;
import com.testhub.core.feature.apiscenario.ApiScenarioRepository;
import com.testhub.core.feature.apienv.ApiEnvironmentRepository;
import com.testhub.core.feature.apicase.ApiCaseRepository;
import com.testhub.core.feature.casemgmt.CapabilityView;
import com.testhub.core.feature.casemgmt.CaseService;
import com.testhub.core.feature.casemgmt.ClientCase;
import com.testhub.core.feature.casemgmt.ClientCaseRepository;
import com.testhub.core.feature.casemgmt.ReviewStatus;
import com.testhub.core.feature.script.ScriptProject;
import com.testhub.core.feature.script.ScriptService;
import com.testhub.core.feature.support.PageSupport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Plan CRUD and case mounting. */
@Service
public class PlanService {

    private final TestPlanRepository planRepository;
    private final PlanCaseRepository planCaseRepository;
    private final PlanRunRepository runRepository;
    private final ClientCaseRepository caseRepository;
    private final ScriptService scriptService;
    private final CaseService caseService;
    private final ApiCaseRepository apiCaseRepository;
    private final ApiEnvironmentRepository environmentRepository;
    private final ApiScenarioRepository scenarioRepository;

    public PlanService(TestPlanRepository planRepository, PlanCaseRepository planCaseRepository,
                       PlanRunRepository runRepository, ClientCaseRepository caseRepository,
                       ScriptService scriptService, CaseService caseService,
                       ApiCaseRepository apiCaseRepository, ApiEnvironmentRepository environmentRepository,
                       ApiScenarioRepository scenarioRepository) {
        this.planRepository = planRepository;
        this.planCaseRepository = planCaseRepository;
        this.runRepository = runRepository;
        this.caseRepository = caseRepository;
        this.scriptService = scriptService;
        this.caseService = caseService;
        this.apiCaseRepository = apiCaseRepository;
        this.environmentRepository = environmentRepository;
        this.scenarioRepository = scenarioRepository;
    }

    public PageResponse<PlanView> list(String keyword, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TestPlan> result = planRepository.findAll(keywordSpec(keyword), pageRequest);
        return PageSupport.of(result, PlanView::of);
    }

    public TestPlan require(String id) {
        return planRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("测试计划不存在: " + id));
    }

    public PlanView get(String id) {
        return PlanView.of(require(id));
    }

    public PlanDetailView detail(String id) {
        TestPlan plan = require(id);
        List<PlanRunView> runs = runRepository.findByPlanIdOrderByStartedAtDesc(id).stream()
                .map(PlanRunView::of)
                .toList();
        return new PlanDetailView(PlanView.of(plan), planCases(id), runs, splitDevices(plan.getDeviceIds()));
    }

    @Transactional
    public PlanView create(PlanRequest request) {
        validate(request);
        AuthPrincipal principal = CurrentUser.require();
        Instant now = Instant.now();
        TestPlan plan = new TestPlan(Ids.newId("plan"), request.name().trim(), request.description(), "READY",
                principal.username(), now, now);
        plan.setEnvironmentId(validateEnvironment(request.environmentId()));
        return PlanView.of(planRepository.save(plan));
    }

    @Transactional
    public PlanView update(String id, PlanRequest request) {
        validate(request);
        TestPlan plan = require(id);
        plan.setName(request.name().trim());
        plan.setDescription(request.description());
        plan.setEnvironmentId(validateEnvironment(request.environmentId()));
        plan.setUpdatedAt(Instant.now());
        return PlanView.of(planRepository.save(plan));
    }

    @Transactional
    public List<String> bindDevices(String id, List<String> deviceIds) {
        TestPlan plan = require(id);
        List<String> normalized = deviceIds == null ? List.of() : deviceIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim).distinct().toList();
        plan.setDeviceIds(normalized.isEmpty() ? null : String.join(",", normalized));
        plan.setUpdatedAt(Instant.now());
        planRepository.save(plan);
        return normalized;
    }

    public static List<String> splitDevices(String deviceIds) {
        if (deviceIds == null || deviceIds.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(deviceIds.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }

    @Transactional
    public void delete(String id) {
        TestPlan plan = require(id);
        planCaseRepository.deleteByPlanId(id);
        // Historical runs are intentionally retained for audit; they are no longer reachable
        // through this plan's run listing.
        planRepository.delete(plan);
    }

    @Transactional
    public List<PlanCaseView> mount(String planId, MountCasesRequest request) {
        TestPlan plan = require(planId);
        List<String> uiCaseIds = request == null || request.caseIds() == null ? List.of() : request.caseIds();
        List<String> apiCaseIds = request == null || request.apiCaseIds() == null ? List.of() : request.apiCaseIds();
        List<String> scenarioIds = request == null || request.scenarioIds() == null ? List.of() : request.scenarioIds();
        if (uiCaseIds.isEmpty() && apiCaseIds.isEmpty() && scenarioIds.isEmpty()) {
            throw ApiException.badRequest("caseIds、apiCaseIds 与 scenarioIds 至少提供一个");
        }
        ExecutorType override = ExecutorType.parseOverride(request.executorTypeOverride());
        Instant now = Instant.now();
        int index = 0;
        for (String caseId : new LinkedHashSet<>(uiCaseIds)) {
            ClientCase clientCase = caseRepository.findById(caseId)
                    .orElseThrow(() -> ApiException.notFound("用例不存在: " + caseId));
            if (clientCase.getReviewStatus() != ReviewStatus.APPROVED) {
                throw new ApiException(HttpStatus.CONFLICT, "CASE_NOT_APPROVED",
                        "用例未通过评审，无法挂载: " + clientCase.getTitle());
            }
            PlanCase mounted = planCaseRepository.findByPlanIdAndCaseId(planId, caseId).orElse(null);
            if (mounted == null) {
                mounted = new PlanCase(Ids.newId("plan-case"), planId, caseId, CaseType.UI, override,
                        now.plusMillis(index++));
            } else {
                mounted.setCaseType(CaseType.UI);
                mounted.setExecutorTypeOverride(override);
            }
            planCaseRepository.save(mounted);
        }
        for (String apiCaseId : new LinkedHashSet<>(apiCaseIds)) {
            ApiCase apiCase = apiCaseRepository.findById(apiCaseId)
                    .orElseThrow(() -> ApiException.notFound("接口用例不存在: " + apiCaseId));
            if (!apiCase.isEnabled()) {
                throw new ApiException(HttpStatus.CONFLICT, "CASE_DISABLED",
                        "接口用例已禁用，无法挂载: " + apiCase.getName());
            }
            PlanCase mounted = planCaseRepository.findByPlanIdAndCaseId(planId, apiCaseId).orElse(null);
            if (mounted == null) {
                mounted = new PlanCase(Ids.newId("plan-case"), planId, apiCaseId, CaseType.API, null,
                        now.plusMillis(index++));
            } else {
                mounted.setCaseType(CaseType.API);
                mounted.setExecutorTypeOverride(null);
            }
            planCaseRepository.save(mounted);
        }
        for (String scenarioId : new LinkedHashSet<>(scenarioIds)) {
            ApiScenario scenario = scenarioRepository.findById(scenarioId)
                    .orElseThrow(() -> ApiException.notFound("API 场景不存在: " + scenarioId));
            if (!scenario.isEnabled()) {
                throw new ApiException(HttpStatus.CONFLICT, "CASE_DISABLED",
                        "API 场景已禁用，无法挂载: " + scenario.getName());
            }
            PlanCase mounted = planCaseRepository.findByPlanIdAndCaseId(planId, scenarioId).orElse(null);
            if (mounted == null) {
                mounted = new PlanCase(Ids.newId("plan-case"), planId, scenarioId, CaseType.SCENARIO,
                        null, now.plusMillis(index++));
            } else {
                mounted.setCaseType(CaseType.SCENARIO);
                mounted.setExecutorTypeOverride(null);
            }
            planCaseRepository.save(mounted);
        }
        plan.setUpdatedAt(Instant.now());
        planRepository.save(plan);
        return planCases(planId);
    }

    @Transactional
    public void unmount(String planId, String caseId) {
        TestPlan plan = require(planId);
        PlanCase mounted = planCaseRepository.findByPlanIdAndCaseId(planId, caseId)
                .orElseThrow(() -> ApiException.notFound("用例未挂载到该计划: " + caseId));
        planCaseRepository.delete(mounted);
        plan.setUpdatedAt(Instant.now());
        planRepository.save(plan);
    }

    public List<PlanCaseView> planCases(String planId) {
        List<PlanCaseView> views = new ArrayList<>();
        for (PlanCase mounted : planCaseRepository.findByPlanIdOrderByCreatedAtAscIdAsc(planId)) {
            if (mounted.getCaseType() == CaseType.SCENARIO) {
                ApiScenario scenario = scenarioRepository.findById(mounted.getCaseId()).orElse(null);
                if (scenario == null) {
                    continue;
                }
                views.add(new PlanCaseView(scenario.getId(), scenario.getName(), null, false, null,
                        null, ExecutorType.SCENARIO, List.of(ExecutorType.SCENARIO),
                        CaseType.SCENARIO, null, null));
                continue;
            }
            if (mounted.getCaseType() == CaseType.API) {
                ApiCase apiCase = apiCaseRepository.findById(mounted.getCaseId()).orElse(null);
                if (apiCase == null) {
                    continue;
                }
                views.add(new PlanCaseView(apiCase.getId(), apiCase.getName(), null, false, null,
                        ExecutorType.API, ExecutorType.API, List.of(ExecutorType.API),
                        CaseType.API, apiCase.getMethod(), apiCase.getPath()));
                continue;
            }
            ClientCase clientCase = caseRepository.findById(mounted.getCaseId()).orElse(null);
            if (clientCase == null) {
                continue;
            }
            ScriptProject script = scriptService.findOrNull(clientCase.getScriptId());
            CapabilityView capability = caseService.capability(script != null, clientCase.getExecutionMode());
            views.add(new PlanCaseView(clientCase.getId(), clientCase.getTitle(), clientCase.getReviewStatus(),
                    script != null, script == null ? null : script.getScriptType(),
                    mounted.getExecutorTypeOverride(), capability.defaultExecutor(),
                    capability.availableExecutors(), CaseType.UI, null, null));
        }
        return views;
    }

    private String validateEnvironment(String environmentId) {
        if (environmentId == null || environmentId.isBlank()) {
            return null;
        }
        var environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> ApiException.notFound("环境不存在: " + environmentId));
        if (!environment.isEnabled()) {
            throw ApiException.badRequest("环境已禁用: " + environment.getName());
        }
        return environmentId;
    }

    private void validate(PlanRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("计划名称不能为空");
        }
    }

    private Specification<TestPlan> keywordSpec(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            predicates.add(cb.like(cb.lower(root.get("description")), pattern));
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
