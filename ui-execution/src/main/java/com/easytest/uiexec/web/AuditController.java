package com.easytest.uiexec.web;

import com.easytest.common.security.RequireRole;
import com.easytest.common.security.Role;
import com.easytest.common.web.PageResponse;
import com.easytest.uiexec.service.AuditService;
import com.easytest.uiexec.web.dto.StatsDtos.AuditLogView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit log query (ADMIN only). */
@RestController
@RequestMapping("/api/v1/ui-execution/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @RequireRole(Role.ADMIN)
    public PageResponse<AuditLogView> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String action) {
        return auditService.list(page, size, action);
    }
}
