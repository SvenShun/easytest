package com.testhub.core.feature.internal;

import com.testhub.core.feature.plan.InternalResultRequest;
import com.testhub.core.feature.plan.PlanRunService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Plan run item result callback from ui-execution (service token required). */
@RestController
@RequestMapping("/internal/v1/plan-runs")
public class InternalPlanRunController {

    private final PlanRunService planRunService;

    public InternalPlanRunController(PlanRunService planRunService) {
        this.planRunService = planRunService;
    }

    @PostMapping("/items/result")
    public ResponseEntity<Void> result(@RequestBody InternalResultRequest request) {
        planRunService.applyResult(request);
        return ResponseEntity.noContent().build();
    }
}
