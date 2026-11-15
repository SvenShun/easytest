package com.easytest.core.feature.caselink;

import com.easytest.common.security.CurrentUser;
import com.easytest.common.util.Ids;
import com.easytest.common.web.ApiException;
import com.easytest.core.feature.apicase.ApiCase;
import com.easytest.core.feature.apicase.ApiCaseRepository;
import com.easytest.core.feature.apiscenario.ApiScenario;
import com.easytest.core.feature.apiscenario.ApiScenarioRepository;
import com.easytest.core.feature.apiscenario.ApiScenarioRunRepository;
import com.easytest.core.feature.caselink.CaseAutomationLinkDtos.LinkRequest;
import com.easytest.core.feature.caselink.CaseAutomationLinkDtos.LinkView;
import com.easytest.core.feature.casemgmt.ClientCase;
import com.easytest.core.feature.casemgmt.ClientCaseRepository;
import com.easytest.core.feature.casemgmt.ReviewStatus;
import com.easytest.core.feature.plan.ExecutorType;
import com.easytest.core.feature.plan.PlanRunItem;
import com.easytest.core.feature.plan.PlanRunItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Explicit, intentionally shallow links between a text case and automation assets. */
@Service
public class CaseAutomationLinkService {

    private static final Set<String> TYPES = Set.of("API_CASE", "API_SCENARIO", "UI_CASE");

    private final CaseAutomationLinkRepository links;
    private final ClientCaseRepository cases;
    private final ApiCaseRepository apiCases;
    private final ApiScenarioRepository scenarios;
    private final ApiScenarioRunRepository scenarioRuns;
    private final PlanRunItemRepository runItems;

    public CaseAutomationLinkService(CaseAutomationLinkRepository links, ClientCaseRepository cases,
                                     ApiCaseRepository apiCases, ApiScenarioRepository scenarios,
                                     ApiScenarioRunRepository scenarioRuns, PlanRunItemRepository runItems) {
        this.links = links;
        this.cases = cases;
        this.apiCases = apiCases;
        this.scenarios = scenarios;
        this.scenarioRuns = scenarioRuns;
        this.runItems = runItems;
    }

    public List<LinkView> list(String caseId) {
        requireCase(caseId);
        return links.findByClientCaseIdOrderByCreatedAtDesc(caseId).stream().map(this::view).toList();
    }

    @Transactional
    public LinkView create(String caseId, LinkRequest request) {
        requireCase(caseId);
        String type = normalizeType(request == null ? null : request.targetType());
        String targetId = request == null ? null : request.targetId();
        if (targetId == null || targetId.isBlank()) {
            throw ApiException.badRequest("targetId 不能为空");
        }
        if (!targetExists(type, targetId)) {
            throw ApiException.notFound("自动化目标不存在: " + targetId);
        }
        if (links.existsByClientCaseIdAndTargetTypeAndTargetId(caseId, type, targetId)) {
            throw new ApiException(HttpStatus.CONFLICT, "LINK_EXISTS", "该自动化用例已关联");
        }
        CaseAutomationLink link = links.save(new CaseAutomationLink(Ids.newId("link"), caseId, type,
                targetId, CurrentUser.require().username(), Instant.now()));
        return view(link);
    }

    @Transactional
    public void delete(String caseId, String linkId) {
        requireCase(caseId);
        CaseAutomationLink link = links.findByIdAndClientCaseId(linkId, caseId)
                .orElseThrow(() -> ApiException.notFound("关联不存在: " + linkId));
        links.delete(link);
    }

    private boolean targetExists(String type, String id) {
        return switch (type) {
            case "API_CASE" -> apiCases.existsById(id);
            case "API_SCENARIO" -> scenarios.existsById(id);
            case "UI_CASE" -> cases.existsById(id);
            default -> false;
        };
    }

    private LinkView view(CaseAutomationLink link) {
        String name = "(已删除)";
        boolean enabled = false;
        String latestStatus = null;
        switch (link.getTargetType()) {
            case "API_CASE" -> {
                ApiCase apiCase = apiCases.findById(link.getTargetId()).orElse(null);
                if (apiCase != null) {
                    name = apiCase.getName();
                    enabled = apiCase.isEnabled();
                }
                latestStatus = latestRun(link.getTargetId(), Set.of(ExecutorType.API));
            }
            case "API_SCENARIO" -> {
                ApiScenario scenario = scenarios.findById(link.getTargetId()).orElse(null);
                if (scenario != null) {
                    name = scenario.getName();
                    enabled = scenario.isEnabled();
                }
                latestStatus = scenarioRuns.findTopByScenarioIdOrderByCreatedAtDesc(link.getTargetId())
                        .map(run -> run.getStatus()).orElse(null);
            }
            case "UI_CASE" -> {
                ClientCase clientCase = cases.findById(link.getTargetId()).orElse(null);
                if (clientCase != null) {
                    name = clientCase.getTitle();
                    enabled = clientCase.getReviewStatus() == ReviewStatus.APPROVED;
                }
                latestStatus = latestRun(link.getTargetId(),
                        Set.of(ExecutorType.SCRIPT, ExecutorType.AI_RUNTIME, ExecutorType.STEP));
            }
            default -> { }
        }
        return new LinkView(link.getId(), link.getTargetType(), link.getTargetId(), name, enabled,
                latestStatus, link.getCreatedBy(), link.getCreatedAt());
    }

    private String latestRun(String caseId, Set<ExecutorType> types) {
        return runItems.findTopByCaseIdAndExecutorTypeInOrderByUpdatedAtDesc(caseId, types)
                .map(item -> item.getStatus().name()).orElse(null);
    }

    private ClientCase requireCase(String caseId) {
        return cases.findById(caseId).orElseThrow(() -> ApiException.notFound("文本用例不存在: " + caseId));
    }

    private String normalizeType(String raw) {
        String type = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw ApiException.badRequest("targetType 仅支持 API_CASE / API_SCENARIO / UI_CASE");
        }
        return type;
    }
}
