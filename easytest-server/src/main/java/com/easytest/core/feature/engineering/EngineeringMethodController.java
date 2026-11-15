package com.easytest.core.feature.engineering;

import com.easytest.common.security.RequireRole;
import com.easytest.common.security.Role;
import com.easytest.core.feature.engineering.EngineeringMethodDtos.ManifestImportRequest;
import com.easytest.core.feature.engineering.EngineeringMethodDtos.MethodView;
import com.easytest.core.feature.engineering.EngineeringMethodDtos.SyncResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/engineering-methods")
@RequireRole({Role.ADMIN, Role.TESTER})
public class EngineeringMethodController {

    private final EngineeringMethodService service;

    public EngineeringMethodController(EngineeringMethodService service) {
        this.service = service;
    }

    @GetMapping
    public List<MethodView> list() {
        return service.list();
    }

    @PostMapping("/sync")
    public SyncResult sync() {
        return service.syncFromAgents();
    }

    @PostMapping("/import")
    public SyncResult importManifest(@RequestBody ManifestImportRequest request) {
        return service.importManifest(request);
    }
}
