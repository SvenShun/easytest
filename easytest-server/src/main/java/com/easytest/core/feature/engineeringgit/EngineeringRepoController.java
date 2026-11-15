package com.easytest.core.feature.engineeringgit;

import com.easytest.common.security.PublicApi;
import com.easytest.common.security.RequireRole;
import com.easytest.common.security.Role;
import com.easytest.common.web.ApiException;
import com.easytest.core.feature.engineeringgit.EngineeringRepoDtos.RepoRequest;
import com.easytest.core.feature.engineeringgit.EngineeringRepoDtos.RepoView;
import com.easytest.core.feature.engineeringgit.EngineeringRepoDtos.ScanResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/engineering-repos")
@RequireRole({Role.ADMIN, Role.TESTER})
public class EngineeringRepoController {

    private final EngineeringRepoService service;

    public EngineeringRepoController(EngineeringRepoService service) {
        this.service = service;
    }

    @GetMapping
    public List<RepoView> list() {
        return service.list();
    }

    @PostMapping
    public RepoView create(@RequestBody RepoRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public RepoView update(@PathVariable String id, @RequestBody RepoRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @GetMapping("/{id}/methods")
    public List<com.easytest.core.feature.engineering.EngineeringMethodDtos.MethodView> methods(
            @PathVariable String id) {
        return service.methods(id);
    }

    @PostMapping("/{id}/methods/{methodId}/run")
    public EngineeringRepoDtos.RunMethodResult runMethod(@PathVariable String id, @PathVariable String methodId,
                                                         @RequestBody(required = false) EngineeringRepoDtos.RunMethodRequest request) {
        return service.runMethod(id, methodId, request);
    }

    @PostMapping("/{id}/scan")
    public ScanResult scan(@PathVariable String id) {
        return service.scan(id);
    }

    /** GitHub/GitLab webhook; token or HMAC protects the endpoint. */
    @PublicApi
    @PostMapping("/{id}/webhook")
    public ScanResult webhook(@PathVariable String id,
                              @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                              @RequestHeader(value = "X-Webhook-Token", required = false) String token,
                              @RequestBody(required = false) String body) {
        if (!service.verifyWebhook(id, signature, token, body == null ? "" : body)) {
            throw ApiException.unauthorized("invalid webhook secret");
        }
        return service.scan(id);
    }
}
