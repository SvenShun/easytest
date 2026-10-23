package com.testhub.core.feature.plan;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plan-runs")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class PlanRunController {

    private final PlanRunService planRunService;

    public PlanRunController(PlanRunService planRunService) {
        this.planRunService = planRunService;
    }

    @GetMapping("/{runId}")
    public PlanRunDetailView get(@PathVariable String runId) {
        return planRunService.getRun(runId);
    }

    @GetMapping("/{runId}/report")
    public PlanReportView report(@PathVariable String runId) {
        return planRunService.report(runId);
    }
}
