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
import java.util.List;
import java.util.Locale;

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
        ScriptProject script = resolveScript(request.scriptId());
        AuthPrincipal principal = CurrentUser.require();
        Instant now = Instant.now();
        ClientCase clientCase = new ClientCase();
        clientCase.setId(Ids.newId("case"));
        apply(clientCase, request);
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
        ScriptProject script = resolveScript(request.scriptId());
        apply(clientCase, request);
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
        return new CaseView(clientCase.getId(), clientCase.getTitle(), clientCase.getBddGiven(),
                clientCase.getBddWhen(), clientCase.getBddThen(), clientCase.getRequirementId(),
                clientCase.getReviewStatus(), clientCase.getSource(), clientCase.getModelId(),
                hasScript ? CaseScriptView.of(script) : null, null, clientCase.getFailureAnalysis(),
                clientCase.getFailureReportUrl(), capability(hasScript), hasScript, reviews,
                clientCase.getCreatedBy(), clientCase.getCreatedAt(), clientCase.getUpdatedAt());
    }

    private void apply(ClientCase clientCase, CaseRequest request) {
        clientCase.setTitle(request.title().trim());
        clientCase.setBddGiven(request.given());
        clientCase.setBddWhen(request.when());
        clientCase.setBddThen(request.then());
        clientCase.setRequirementId(isBlank(request.requirementId()) ? null : request.requirementId().trim());
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
