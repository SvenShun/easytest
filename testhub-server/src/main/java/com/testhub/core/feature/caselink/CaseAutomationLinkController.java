package com.testhub.core.feature.caselink;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.core.feature.caselink.CaseAutomationLinkDtos.LinkRequest;
import com.testhub.core.feature.caselink.CaseAutomationLinkDtos.LinkView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cases")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class CaseAutomationLinkController {

    private final CaseAutomationLinkService service;

    public CaseAutomationLinkController(CaseAutomationLinkService service) {
        this.service = service;
    }

    @GetMapping("/{caseId}/automation-links")
    public List<LinkView> list(@PathVariable String caseId) {
        return service.list(caseId);
    }

    @PostMapping("/{caseId}/automation-links")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public LinkView create(@PathVariable String caseId, @RequestBody LinkRequest request) {
        return service.create(caseId, request);
    }

    @DeleteMapping("/{caseId}/automation-links/{linkId}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void delete(@PathVariable String caseId, @PathVariable String linkId) {
        service.delete(caseId, linkId);
    }
}
