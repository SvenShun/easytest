package io.metersphere.uiexec;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.controller.handler.ResultHolder;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.uiexec.UiExecutionDtos.AssociateRequest;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Associates a MeterSphere functional case with a test plan (returns plan-case id). */
@RestController
@RequestMapping("/test-plan/ui-execution")
public class UiExecutionAssociationController {

    private final UiExecutionService service;

    public UiExecutionAssociationController(UiExecutionService service) {
        this.service = service;
    }

    @PostMapping("/associate")
    @RequiresPermissions(PermissionConstants.TEST_PLAN_READ_ASSOCIATION)
    public ResultHolder associate(@RequestBody AssociateRequest request) {
        String planCaseId = service.associate(request.testPlanId(), request.caseId(), SessionUtils.getUserId());
        return ResultHolder.success(Map.of("testPlanCaseId", planCaseId));
    }
}
