package com.testhub.core.feature.casemgmt;

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

import java.util.List;

@RestController
@RequestMapping("/api/v1/cases")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PostMapping
    public CaseView create(@RequestBody CaseRequest request) {
        return caseService.create(request);
    }

    @GetMapping
    public PageResponse<CaseView> list(@RequestParam(required = false) String reviewStatus,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String requirementId,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return caseService.search(reviewStatus, keyword, requirementId, page, size);
    }

    @GetMapping("/{id}")
    public CaseView get(@PathVariable String id) {
        return caseService.get(id);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PutMapping("/{id}")
    public CaseView update(@PathVariable String id, @RequestBody CaseRequest request) {
        return caseService.update(id, request);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        caseService.delete(id);
    }

    @RequireRole({Role.ADMIN, Role.TESTER})
    @PostMapping("/{id}/review")
    public CaseView review(@PathVariable String id, @RequestBody ReviewRequest request) {
        return caseService.review(id, request);
    }

    @GetMapping("/{id}/reviews")
    public List<CaseReviewView> reviews(@PathVariable String id) {
        return caseService.reviews(id);
    }
}
