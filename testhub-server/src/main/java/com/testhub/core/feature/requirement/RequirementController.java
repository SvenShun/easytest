package com.testhub.core.feature.requirement;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.PageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/requirements")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class RequirementController {

    private final RequirementService service;

    public RequirementController(RequirementService service) {
        this.service = service;
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PostMapping
    public RequirementView create(@RequestBody RequirementRequest request) {
        return service.create(request);
    }

    @GetMapping
    public PageResponse<RequirementView> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return service.search(keyword, page, size);
    }

    @GetMapping("/{id}")
    public RequirementView get(@PathVariable String id) {
        return service.get(id);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PutMapping("/{id}")
    public RequirementView update(@PathVariable String id, @RequestBody RequirementRequest request) {
        return service.update(id, request);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
