package com.testhub.core.feature.casemgmt;

import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.plan.ExecutorType;
import com.testhub.core.feature.plan.PlanCaseRepository;
import com.testhub.core.feature.script.ScriptProject;
import com.testhub.core.feature.script.ScriptService;
import com.testhub.core.feature.script.ScriptTicketService;
import com.testhub.core.feature.support.AuditService;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Case CRUD, review state machine, capability and execution context. */
@Service
public class CaseService {

    private final ClientCaseRepository caseRepository;
    private final CaseReviewRepository reviewRepository;
    private final PlanCaseRepository planCaseRepository;
    private final ScriptService scriptService;
    private final ScriptTicketService ticketService;
    private final AuditService auditService;

    public CaseService(ClientCaseRepository caseRepository, CaseReviewRepository reviewRepository,
                       PlanCaseRepository planCaseRepository, ScriptService scriptService,
                       ScriptTicketService ticketService, AuditService auditService) {
        this.caseRepository = caseRepository;
        this.reviewRepository = reviewRepository;
        this.planCaseRepository = planCaseRepository;
        this.scriptService = scriptService;
        this.ticketService = ticketService;
        this.auditService = auditService;
    }

    public PageResponse<CaseView> search(String reviewStatus, String keyword, String requirementId,
                                         Integer page, Integer size) {
        ReviewStatus status = parseStatus(reviewStatus);
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ClientCase> result = caseRepository.findAll(filter(status, keyword, requirementId), pageRequest);
        List<CaseView> items = result.getContent().stream()
                .map(clientCase -> view(clientCase, scriptService.findOrNull(clientCase.getScriptId())))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), result.getNumber() + 1, result.getSize());
    }

    public ClientCase require(String id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("用例不存在: " + id));
    }

    public CaseView get(String id) {
        ClientCase clientCase = require(id);
        ScriptProject script = scriptService.findOrNull(clientCase.getScriptId());
        return view(clientCase, script);
    }

    @Transactional
    public CaseView create(CaseRequest request) {
        validate(request);
        ExecutionMode executionMode = ExecutionMode.parseOrDefault(request.executionMode(), ExecutionMode.AI);
        List<StepDraft> stepDrafts = request.steps() == null ? List.of() : parseSteps(request.steps());
        requireExecutableSteps(executionMode, stepDrafts);
        ScriptProject script = resolveScript(request.scriptId());
        AuthPrincipal principal = CurrentUser.require();
        Instant now = Instant.now();
        ClientCase clientCase = new ClientCase();
        clientCase.setId(Ids.newId("case"));
        apply(clientCase, request, executionMode);
        applySteps(clientCase, stepDrafts);
        clientCase.setScriptId(script == null ? null : script.getId());
        clientCase.setSource(CaseSource.MANUAL);
        clientCase.setReviewStatus(ReviewStatus.DRAFT);
        clientCase.setModelId(null);
        clientCase.setCreatedBy(principal.username());
        clientCase.setCreatedAt(now);
        clientCase.setUpdatedAt(now);
        return view(caseRepository.save(clientCase), script);
    }

    @Transactional
    public CaseView update(String id, CaseRequest request) {
        validate(request);
        ClientCase clientCase = require(id);
        ExecutionMode executionMode = ExecutionMode.parseOrDefault(request.executionMode(),
                clientCase.getExecutionMode());
        List<StepDraft> stepDrafts = request.steps() == null ? null : parseSteps(request.steps());
        if (stepDrafts != null) {
            requireExecutableSteps(executionMode, stepDrafts);
        } else if (executionMode == ExecutionMode.STEPS
                && clientCase.getSteps().stream().noneMatch(CaseStep::isEnabled)) {
            throw ApiException.badRequest("STEP 执行模式的用例至少需要一个启用的步骤");
        }
        ScriptProject script = resolveScript(request.scriptId());
        apply(clientCase, request, executionMode);
        if (stepDrafts != null) {
            applySteps(clientCase, stepDrafts);
        }
        clientCase.setScriptId(script == null ? null : script.getId());
        clientCase.setUpdatedAt(Instant.now());
        return view(caseRepository.save(clientCase), script);
    }

    @Transactional
    public void delete(String id) {
        ClientCase clientCase = require(id);
        if (planCaseRepository.existsByCaseId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "CASE_IN_USE",
                    "用例已被测试计划引用，请先从计划中移除后再删除");
        }
        reviewRepository.deleteByCaseId(id);
        caseRepository.delete(clientCase);
    }

    @Transactional
    public CaseView review(String id, ReviewRequest request) {
        ClientCase clientCase = require(id);
        ReviewAction action = parseAction(request == null ? null : request.action());
        ReviewStatus from = clientCase.getReviewStatus();
        ReviewStatus to;
        switch (action) {
            case SUBMIT -> {
                if (from != ReviewStatus.DRAFT && from != ReviewStatus.REJECTED) {
                    throw invalidTransition(from, action);
                }
                to = ReviewStatus.PENDING;
            }
            case APPROVE -> {
                if (from != ReviewStatus.PENDING) {
                    throw invalidTransition(from, action);
                }
                to = ReviewStatus.APPROVED;
            }
            case REJECT -> {
                if (from != ReviewStatus.PENDING) {
                    throw invalidTransition(from, action);
                }
                to = ReviewStatus.REJECTED;
            }
            default -> throw invalidTransition(from, action);
        }
        clientCase.setReviewStatus(to);
        clientCase.setUpdatedAt(Instant.now());
        caseRepository.save(clientCase);
        reviewRepository.save(new CaseReview(Ids.newId("review"), id, action,
                currentActor(), request.comment(), Instant.now()));
        auditService.record(AuditService.CASE_REVIEW, "case:" + id,
                action.name() + " " + from + "->" + to);
        return view(clientCase, scriptService.findOrNull(clientCase.getScriptId()));
    }

    public List<CaseReviewView> reviews(String caseId) {
        require(caseId);
        return reviewRepository.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
                .map(CaseReviewView::of)
                .toList();
    }

    public CapabilityView capability(boolean hasScript) {
        return capability(hasScript, ExecutionMode.AI);
    }

    public CapabilityView capability(boolean hasScript, ExecutionMode executionMode) {
        if (executionMode == ExecutionMode.STEPS) {
            return new CapabilityView(List.of(ExecutorType.STEP), ExecutorType.STEP);
        }
        if (hasScript) {
            return new CapabilityView(List.of(ExecutorType.SCRIPT, ExecutorType.AI_RUNTIME),
                    ExecutorType.SCRIPT);
        }
        return new CapabilityView(List.of(ExecutorType.AI_RUNTIME), ExecutorType.AI_RUNTIME);
    }

    public ExecutionContextView executionContext(String caseId) {
        ClientCase clientCase = require(caseId);
        ScriptProject script = scriptService.findOrNull(clientCase.getScriptId());
        ExecutionScriptView scriptView = script == null ? null
                : ExecutionScriptView.of(script, ticketService.bundleUrl(script.getId()));
        BddView bdd = new BddView(clientCase.getBddGiven(), clientCase.getBddWhen(), clientCase.getBddThen());
        return new ExecutionContextView(clientCase.getId(), clientCase.getTitle(), clientCase.getReviewStatus(),
                bdd, scriptView, clientCase.getFailureAnalysis());
    }

    @Transactional
    public void updateFailureAnalysis(String caseId, FailureAnalysisRequest request) {
        ClientCase clientCase = require(caseId);
        clientCase.setFailureAnalysis(request.summary());
        clientCase.setFailureReportUrl(request.reportUrl());
        clientCase.setFailureTaskId(request.taskId());
        clientCase.setFailureUpdatedAt(Instant.now());
        clientCase.setUpdatedAt(Instant.now());
        caseRepository.save(clientCase);
    }

    public CaseView view(ClientCase clientCase, ScriptProject script) {
        boolean hasScript = script != null;
        List<CaseReviewView> reviews = reviewRepository
                .findByCaseIdOrderByCreatedAtAsc(clientCase.getId()).stream()
                .map(CaseReviewView::of)
                .toList();
        List<CaseStepView> steps = clientCase.getSteps().stream()
                .sorted(Comparator.comparingInt(CaseStep::getSeq))
                .map(CaseStepView::of)
                .toList();
        return new CaseView(clientCase.getId(), clientCase.getTitle(), clientCase.getBddGiven(),
                clientCase.getBddWhen(), clientCase.getBddThen(), clientCase.getRequirementId(),
                clientCase.getReviewStatus(), clientCase.getSource(), clientCase.getModelId(),
                hasScript ? CaseScriptView.of(script) : null, null, clientCase.getFailureAnalysis(),
                clientCase.getFailureReportUrl(), capability(hasScript, clientCase.getExecutionMode()),
                hasScript, clientCase.getExecutionMode(), steps, reviews,
                clientCase.getCreatedBy(), clientCase.getCreatedAt(), clientCase.getUpdatedAt());
    }

    private void apply(ClientCase clientCase, CaseRequest request, ExecutionMode executionMode) {
        clientCase.setTitle(request.title().trim());
        clientCase.setBddGiven(request.given());
        clientCase.setBddWhen(request.when());
        clientCase.setBddThen(request.then());
        clientCase.setRequirementId(isBlank(request.requirementId()) ? null : request.requirementId().trim());
        clientCase.setExecutionMode(executionMode);
    }

    private ScriptProject resolveScript(String scriptId) {
        if (isBlank(scriptId)) {
            return null;
        }
        ScriptProject script = scriptService.findOrNull(scriptId.trim());
        if (script == null) {
            throw ApiException.notFound("脚本不存在: " + scriptId);
        }
        return script;
    }

    private void validate(CaseRequest request) {
        if (request == null || isBlank(request.title())) {
            throw ApiException.badRequest("用例标题不能为空");
        }
    }

    private void requireExecutableSteps(ExecutionMode executionMode, List<StepDraft> steps) {
        if (executionMode == ExecutionMode.STEPS && steps.stream().noneMatch(StepDraft::enabled)) {
            throw ApiException.badRequest("STEP 执行模式的用例至少需要一个启用的步骤");
        }
    }

    private List<StepDraft> parseSteps(List<CaseStepRequest> requests) {
        if (requests.size() > 500) {
            throw ApiException.badRequest("单个用例最多支持 500 个步骤");
        }
        List<StepDraft> drafts = new ArrayList<>();
        Set<Integer> seqs = new HashSet<>();
        int index = 0;
        for (CaseStepRequest request : requests) {
            index++;
            if (request == null) {
                throw ApiException.badRequest("步骤不能为空");
            }
            StepType stepType = StepType.parse(request.stepType());
            int seq = request.seq() == null || request.seq() <= 0 ? index : request.seq();
            if (!seqs.add(seq)) {
                throw ApiException.badRequest("步骤 seq 重复: " + seq);
            }
            int timeoutMs = request.timeoutMs() == null ? 0 : request.timeoutMs();
            if (timeoutMs < 0) {
                throw ApiException.badRequest("步骤超时时间不能为负数");
            }
            String selector = trimToNull(request.selector());
            String value = request.value();
            String assertValue = request.assertValue();
            SelectorType selectorType = normalizeSelectorType(stepType,
                    SelectorType.parse(request.selectorType()));
            validateStep(stepType, selectorType, selector, value, assertValue);
            boolean enabled = request.enabled() == null || request.enabled();
            drafts.add(new StepDraft(seq, stepType, selectorType, selector, value, assertValue, timeoutMs,
                    enabled));
        }
        drafts.sort(Comparator.comparingInt(StepDraft::seq));
        return drafts;
    }

    private SelectorType normalizeSelectorType(StepType stepType, SelectorType selectorType) {
        return switch (stepType) {
            case TAP, SWIPE -> SelectorType.COORD;
            case CLICK, INPUT, ASSERT_TEXT, ASSERT_EXISTS -> selectorType == null ? SelectorType.TEXT : selectorType;
            default -> null;
        };
    }

    private void validateStep(StepType stepType, SelectorType selectorType, String selector, String value,
                              String assertValue) {
        switch (stepType) {
            case CLICK -> {
                requireSelector(stepType, selector);
                if (selectorType == SelectorType.COORD) {
                    parsePoints(selector, 2, "CLICK 坐标");
                } else if (selectorType != SelectorType.TEXT && selectorType != SelectorType.ID) {
                    throw ApiException.badRequest("CLICK 仅支持 TEXT / ID / COORD selectorType");
                }
            }
            case INPUT -> {
                requireSelector(stepType, selector);
                if (selectorType != SelectorType.TEXT && selectorType != SelectorType.ID) {
                    throw ApiException.badRequest("INPUT 仅支持 TEXT / ID selectorType");
                }
            }
            case TAP -> parsePoints(firstNonBlank(selector, value), 2, "TAP 坐标");
            case SWIPE -> {
                requireSelector(stepType, firstNonBlank(value, assertValue, selector));
                parsePoints(firstNonBlank(value, assertValue, selector), 4, "SWIPE 坐标");
            }
            case WAIT -> {
                String raw = firstNonBlank(value, selector);
                if (raw == null) {
                    throw ApiException.badRequest("WAIT 需要 value（毫秒）");
                }
                parseLong(raw, "WAIT 毫秒");
            }
            case LAUNCH -> requireSelector(stepType, firstNonBlank(value, selector));
            case ASSERT_TEXT -> requireSelector(stepType, firstNonBlank(assertValue, value, selector));
            case ASSERT_EXISTS -> {
                requireSelector(stepType, selector);
                if (selectorType == SelectorType.COORD) {
                    parsePoints(selector, 2, "ASSERT_EXISTS 坐标");
                }
            }
            case BACK, HOME, SCREENSHOT -> {
                // no selector required
            }
        }
    }

    private void requireSelector(StepType stepType, String selector) {
        if (isBlank(selector)) {
            throw ApiException.badRequest(stepType.name() + " 步骤需要 selector");
        }
    }

    private int[] parsePoints(String raw, int expected, String label) {
        if (isBlank(raw)) {
            throw ApiException.badRequest(label + "不能为空");
        }
        String[] parts = raw.split(",");
        if (parts.length != expected) {
            throw ApiException.badRequest(label + "需要 " + expected + " 个数字，格式为 "
                    + (expected == 2 ? "x,y" : "x1,y1,x2,y2"));
        }
        int[] values = new int[expected];
        for (int i = 0; i < expected; i++) {
            long parsed = parseLong(parts[i].trim(), label);
            if (parsed < 0 || parsed > Integer.MAX_VALUE) {
                throw ApiException.badRequest(label + "必须是非负整数");
            }
            values[i] = (int) parsed;
        }
        return values;
    }

    private long parseLong(String raw, String label) {
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0) {
                throw ApiException.badRequest(label + "必须是非负整数");
            }
            return value;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(label + "必须是整数: " + raw);
        }
    }

    private void applySteps(ClientCase clientCase, List<StepDraft> drafts) {
        List<CaseStep> existing = new ArrayList<>(clientCase.getSteps());
        List<CaseStep> result = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            StepDraft draft = drafts.get(i);
            CaseStep step = i < existing.size() ? existing.get(i) : new CaseStep(Ids.newId("case-step"));
            step.setClientCase(clientCase);
            step.setSeq(draft.seq());
            step.setStepType(draft.stepType());
            step.setSelectorType(draft.selectorType());
            step.setSelector(draft.selector());
            step.setValue(draft.value());
            step.setAssertValue(draft.assertValue());
            step.setTimeoutMs(draft.timeoutMs());
            step.setEnabled(draft.enabled());
            result.add(step);
        }
        clientCase.getSteps().clear();
        clientCase.getSteps().addAll(result);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record StepDraft(int seq, StepType stepType, SelectorType selectorType, String selector,
                             String value, String assertValue, int timeoutMs, boolean enabled) {
    }

    private ReviewStatus parseStatus(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return ReviewStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("未知的评审状态: " + value);
        }
    }

    private ReviewAction parseAction(String value) {
        if (isBlank(value)) {
            throw ApiException.badRequest("评审动作不能为空");
        }
        try {
            return ReviewAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_REVIEW_TRANSITION",
                    "不支持的评审动作: " + value);
        }
    }

    private ApiException invalidTransition(ReviewStatus from, ReviewAction action) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_REVIEW_TRANSITION",
                "非法评审流转: " + from + " -" + action + "->");
    }

    private String currentActor() {
        AuthPrincipal principal = CurrentUser.get();
        return principal == null ? "system" : principal.username();
    }

    private Specification<ClientCase> filter(ReviewStatus reviewStatus, String keyword, String requirementId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (reviewStatus != null) {
                predicates.add(cb.equal(root.get("reviewStatus"), reviewStatus));
            }
            if (!isBlank(requirementId)) {
                predicates.add(cb.equal(root.get("requirementId"), requirementId.trim()));
            }
            if (!isBlank(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("bddGiven")), pattern),
                        cb.like(cb.lower(root.get("bddWhen")), pattern),
                        cb.like(cb.lower(root.get("bddThen")), pattern)));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
