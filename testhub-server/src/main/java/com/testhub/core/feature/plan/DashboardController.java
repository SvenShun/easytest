package com.testhub.core.feature.plan;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.core.feature.apicase.ApiCaseRepository;
import com.testhub.core.feature.casemgmt.ClientCaseRepository;
import com.testhub.core.feature.casemgmt.ReviewStatus;
import com.testhub.core.feature.requirement.RequirementRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lightweight test-report dashboard (Sonic results chart parity). */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class DashboardController {

    private final ClientCaseRepository cases;
    private final RequirementRepository requirements;
    private final TestPlanRepository plans;
    private final PlanRunRepository runs;
    private final PlanRunItemRepository items;
    private final ApiCaseRepository apiCases;

    public DashboardController(ClientCaseRepository cases, RequirementRepository requirements,
                               TestPlanRepository plans, PlanRunRepository runs,
                               PlanRunItemRepository items, ApiCaseRepository apiCases) {
        this.cases = cases;
        this.requirements = requirements;
        this.plans = plans;
        this.runs = runs;
        this.items = items;
        this.apiCases = apiCases;
    }

    @GetMapping
    public DashboardView summary() {
        long totalCases = cases.count();
        long approved = cases.countByReviewStatus(ReviewStatus.APPROVED);
        long totalRequirements = requirements.count();
        long totalPlans = plans.count();
        long totalApiCases = apiCases.count();

        List<PlanRun> allRuns = runs.findAll(Sort.by(Sort.Direction.DESC, "startedAt"));
        List<PlanRun> recent = allRuns.stream().limit(10).toList();
        long success = 0;
        long failed = 0;
        long timeout = 0;
        long cancelled = 0;
        long totalItems = 0;
        long passedItems = 0;
        for (PlanRun run : allRuns) {
            success += run.getSuccess();
            failed += run.getFailed();
            timeout += run.getTimeout();
            cancelled += run.getCancelled();
            totalItems += run.getTotal();
            passedItems += run.getSuccess();
        }
        double passRate = totalItems == 0 ? 0 : Math.round(passedItems * 1000.0 / totalItems) / 10.0;

        Map<String, Integer> failures = new LinkedHashMap<>();
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<PlanRunItem> recentItems = items.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .filter(item -> item.getUpdatedAt() != null && item.getUpdatedAt().isAfter(since))
                .limit(1000)
                .toList();
        for (PlanRunItem item : recentItems) {
            if (item.getStatus().isFailure() && item.getFailureReason() != null) {
                failures.merge(item.getFailureReason(), 1, Integer::sum);
            }
        }
        List<FailureCount> failureList = new ArrayList<>();
        failures.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(8)
                .forEach(entry -> failureList.add(new FailureCount(entry.getKey(), entry.getValue())));

        List<RecentRun> recentViews = recent.stream()
                .map(run -> new RecentRun(run.getId(), run.getPlanId(), run.getStatus().name(),
                        run.getTotal(), run.getSuccess(), run.getFailed(), run.getTimeout(),
                        run.getStartedAt(), run.getFinishedAt()))
                .toList();

        return new DashboardView(totalRequirements, totalCases, approved, totalPlans, totalApiCases,
                allRuns.size(), success, failed, timeout, cancelled, totalItems, passRate,
                recentViews, failureList);
    }

    public record FailureCount(String reason, int count) {
    }

    public record RecentRun(String runId, String planId, String status, int total, int success,
                            int failed, int timeout, Instant startedAt, Instant finishedAt) {
    }

    public record DashboardView(long requirements, long cases, long approvedCases, long plans,
                                long apiCases, long runs, long success, long failed, long timeout,
                                long cancelled, long totalItems, double passRate,
                                List<RecentRun> recentRuns, List<FailureCount> failures) {
    }
}
