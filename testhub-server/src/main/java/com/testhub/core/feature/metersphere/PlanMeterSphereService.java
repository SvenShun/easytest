package com.testhub.core.feature.metersphere;

import com.testhub.common.web.ApiException;
import com.testhub.core.feature.plan.PlanCase;
import com.testhub.core.feature.plan.PlanCaseRepository;
import com.testhub.core.feature.plan.TestPlan;
import com.testhub.core.feature.plan.TestPlanRepository;
import com.testhub.core.feature.metersphere.MeterSphereMappingDtos.CaseMappingRequest;
import com.testhub.core.feature.metersphere.MeterSphereMappingDtos.CaseMappingView;
import com.testhub.core.feature.metersphere.MeterSphereMappingDtos.PlanMappingRequest;
import com.testhub.core.feature.metersphere.MeterSphereMappingDtos.PlanMappingView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Links a TestHub plan (and its mounted cases) to MeterSphere test-plan ids so
 * execution results can be written back. Mapping is explicit: TestHub stays the
 * source of truth for plans, MeterSphere receives the results.
 */
@Service
public class PlanMeterSphereService {

    private final TestPlanRepository planRepository;
    private final PlanCaseRepository planCaseRepository;

    public PlanMeterSphereService(TestPlanRepository planRepository, PlanCaseRepository planCaseRepository) {
        this.planRepository = planRepository;
        this.planCaseRepository = planCaseRepository;
    }

    @Transactional
    public PlanMappingView linkPlan(String planId, PlanMappingRequest request) {
        TestPlan plan = requirePlan(planId);
        if (request == null || isBlank(request.testPlanId())) {
            throw ApiException.badRequest("testPlanId 不能为空");
        }
        plan.setMetersphereTestPlanId(request.testPlanId().trim());
        plan.setUpdatedAt(Instant.now());
        planRepository.save(plan);
        return view(planId);
    }

    @Transactional
    public PlanMappingView linkCase(String planId, String caseId, CaseMappingRequest request) {
        requirePlan(planId);
        PlanCase planCase = planCaseRepository.findByPlanIdAndCaseId(planId, caseId)
                .orElseThrow(() -> ApiException.notFound("计划中未挂载该用例: " + caseId));
        if (request == null || isBlank(request.metersphereCaseId())
                || isBlank(request.metersphereTestPlanCaseId())) {
            throw ApiException.badRequest("metersphereCaseId 与 metersphereTestPlanCaseId 均不能为空");
        }
        planCase.setMetersphereCaseId(request.metersphereCaseId().trim());
        planCase.setMetersphereTestPlanCaseId(request.metersphereTestPlanCaseId().trim());
        planCaseRepository.save(planCase);
        return view(planId);
    }

    @Transactional(readOnly = true)
    public PlanMappingView view(String planId) {
        TestPlan plan = requirePlan(planId);
        List<CaseMappingView> cases = planCaseRepository.findByPlanIdOrderByCreatedAtAscIdAsc(planId).stream()
                .map(planCase -> new CaseMappingView(planCase.getCaseId(),
                        planCase.getMetersphereCaseId(), planCase.getMetersphereTestPlanCaseId()))
                .toList();
        return new PlanMappingView(plan.getId(), plan.getMetersphereTestPlanId(), cases);
    }

    private TestPlan requirePlan(String planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> ApiException.notFound("测试计划不存在: " + planId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
