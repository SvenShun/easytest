package com.easytest.core.feature.project;

import com.easytest.common.security.RequireRole;
import com.easytest.common.security.Role;
import com.easytest.core.feature.project.ProjectDtos.MemberRequest;
import com.easytest.core.feature.project.ProjectDtos.MemberView;
import com.easytest.core.feature.project.ProjectDtos.ProjectDetail;
import com.easytest.core.feature.project.ProjectDtos.ProjectRequest;
import com.easytest.core.feature.project.ProjectDtos.ProjectView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProjectView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ProjectDetail detail(@PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ProjectView create(@RequestBody ProjectRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ProjectView update(@PathVariable String id, @RequestBody ProjectRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @RequireRole({Role.ADMIN})
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @GetMapping("/{id}/members")
    public List<MemberView> members(@PathVariable String id) {
        return service.members(id);
    }

    @PostMapping("/{id}/members")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public MemberView addMember(@PathVariable String id, @RequestBody MemberRequest request) {
        return service.addMember(id, request);
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void removeMember(@PathVariable String id, @PathVariable String memberId) {
        service.removeMember(id, memberId);
    }
}
