package com.testhub.core.feature.apienv;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.apienv.ApiEnvironmentDtos.EnvironmentRequest;
import com.testhub.core.feature.apienv.ApiEnvironmentDtos.EnvironmentView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API environment management. */
@RestController
@RequestMapping("/api/v1/api-environments")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class ApiEnvironmentController {

    private final ApiEnvironmentService service;

    public ApiEnvironmentController(ApiEnvironmentService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<EnvironmentView> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return service.list(keyword, page, size);
    }

    @GetMapping("/{id}")
    public EnvironmentView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    @RequireRole({Role.ADMIN, Role.TESTER})
    public EnvironmentView create(@Valid @RequestBody EnvironmentRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public EnvironmentView update(@PathVariable String id, @Valid @RequestBody EnvironmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
