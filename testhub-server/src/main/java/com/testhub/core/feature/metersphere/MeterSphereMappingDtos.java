package com.testhub.core.feature.metersphere;

import java.util.List;

/** Request/response shapes for linking TestHub plans to MeterSphere test plans. */
public final class MeterSphereMappingDtos {

    private MeterSphereMappingDtos() {
    }

    public record PlanMappingRequest(String testPlanId) {
    }

    public record CaseMappingRequest(String metersphereCaseId, String metersphereTestPlanCaseId) {
    }

    public record CaseMappingView(String caseId, String metersphereCaseId, String metersphereTestPlanCaseId) {
    }

    public record PlanMappingView(String planId, String metersphereTestPlanId, List<CaseMappingView> cases) {
    }
}
