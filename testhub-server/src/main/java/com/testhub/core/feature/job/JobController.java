package com.testhub.core.feature.job;

import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.job.JobDtos.JobRequest;
import com.testhub.core.feature.job.JobDtos.JobView;
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
@RequestMapping("/api/v1/jobs")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class JobController {

    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<JobView> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        return service.list(keyword, page, size);
    }

    @GetMapping("/{id}")
    public JobView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    @RequireRole({Role.ADMIN, Role.TESTER})
    public JobView create(@RequestBody JobRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public JobView update(@PathVariable String id, @RequestBody JobRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/toggle")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public JobView toggle(@PathVariable String id, @RequestParam boolean enabled) {
        return service.toggle(id, enabled);
    }

    @PostMapping("/{id}/run")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public JobView run(@PathVariable String id) {
        return service.runNow(id);
    }

    @DeleteMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
