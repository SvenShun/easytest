package com.testhub.core.feature.casemgmt;

/** Internal execution context (contract §1.8). */
public record ExecutionContextView(String caseId, String title, ReviewStatus reviewStatus, BddView bdd,
                                   ExecutionScriptView script, String failureAnalysis) {
}
