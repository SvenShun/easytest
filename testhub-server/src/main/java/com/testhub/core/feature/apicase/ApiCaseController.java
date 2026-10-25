package com.testhub.core.feature.apicase;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.apicase.ApiCaseDtos.ApiCaseRequest;
import com.testhub.core.feature.apicase.ApiCaseDtos.ApiCaseView;
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

/** API test case management. */
@RestController
@RequestMapping("/api/v1/api-cases")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class ApiCaseController {

    private final ApiCaseService service;

    public ApiCaseController(ApiCaseService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ApiCaseView> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return service.list(keyword, page, size);
    }

    @GetMapping("/{id}")
    public ApiCaseView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ApiCaseView create(@Valid @RequestBody ApiCaseRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ApiCaseView update(@PathVariable String id, @Valid @RequestBody ApiCaseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
