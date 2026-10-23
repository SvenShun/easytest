package com.testhub.core.feature.internal;

import com.testhub.core.feature.casemgmt.CaseService;
import com.testhub.core.feature.casemgmt.ExecutionContextView;
import com.testhub.core.feature.casemgmt.FailureAnalysisRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-token endpoints consumed by ui-execution / agents. */
@RestController
@RequestMapping("/internal/v1/cases")
public class InternalCaseController {

    private final CaseService caseService;

    public InternalCaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/{caseId}/execution-context")
    public ExecutionContextView executionContext(@PathVariable String caseId) {
        return caseService.executionContext(caseId);
    }

    @PutMapping("/{caseId}/failure-analysis")
    public ResponseEntity<Void> failureAnalysis(@PathVariable String caseId,
                                                @RequestBody FailureAnalysisRequest request) {
        caseService.updateFailureAnalysis(caseId, request);
        return ResponseEntity.noContent().build();
    }
}
