package com.testhub.core.feature.plan;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.metersphere.MeterSphereMappingDtos.CaseMappingRequest;
import com.testhub.core.feature.metersphere.MeterSphereMappingDtos.PlanMappingRequest;
import com.testhub.core.feature.metersphere.MeterSphereMappingDtos.PlanMappingView;
import com.testhub.core.feature.metersphere.PlanMeterSphereService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class PlanController {

    private final PlanService planService;
    private final PlanRunService planRunService;
    private final PlanMeterSphereService meterSphereService;

    public PlanController(PlanService planService, PlanRunService planRunService,
                          PlanMeterSphereService meterSphereService) {
        this.planService = planService;
        this.planRunService = planRunService;
        this.meterSphereService = meterSphereService;
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PostMapping
    public PlanView create(@RequestBody PlanRequest request) {
        return planService.create(request);
    }

    @GetMapping
    public PageResponse<PlanView> list(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size) {
        return planService.list(keyword, page, size);
    }

    @GetMapping("/{id}")
    public PlanDetailView detail(@PathVariable String id) {
        return planService.detail(id);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PutMapping("/{id}")
    public PlanView update(@PathVariable String id, @RequestBody PlanRequest request) {
        return planService.update(id, request);
    }

    public record BindDevicesRequest(List<String> deviceIds) {
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PutMapping("/{id}/devices")
    public List<String> bindDevices(@PathVariable String id, @RequestBody BindDevicesRequest request) {
        return planService.bindDevices(id, request == null ? List.of() : request.deviceIds());
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        planService.delete(id);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PostMapping("/{id}/cases")
    public List<PlanCaseView> mount(@PathVariable String id, @RequestBody MountCasesRequest request) {
        return planService.mount(id, request);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @DeleteMapping("/{id}/cases/{caseId}")
    public ResponseEntity<Void> unmount(@PathVariable String id, @PathVariable String caseId) {
        planService.unmount(id, caseId);
        return ResponseEntity.noContent().build();
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PutMapping("/{id}/metersphere")
    public PlanMappingView linkMeterSpherePlan(@PathVariable String id,
                                               @RequestBody PlanMappingRequest request) {
        return meterSphereService.linkPlan(id, request);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PutMapping("/{id}/cases/{caseId}/metersphere")
    public PlanMappingView linkMeterSphereCase(@PathVariable String id, @PathVariable String caseId,
                                               @RequestBody CaseMappingRequest request) {
        return meterSphereService.linkCase(id, caseId, request);
    }

    @GetMapping("/{id}/metersphere")
    public PlanMappingView meterSphereMapping(@PathVariable String id) {
        return meterSphereService.view(id);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PostMapping("/{id}/trigger")
    public TriggerResponse trigger(@PathVariable String id, @RequestBody(required = false) TriggerRequest request) {
        return planRunService.trigger(id, request);
    }

    @GetMapping("/{id}/runs")
    public PageResponse<PlanRunView> runs(@PathVariable String id,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return planRunService.listRuns(id, page, size);
    }
}
