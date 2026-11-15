package com.easytest.core.feature.project;

import com.easytest.common.security.CurrentUser;
import com.easytest.common.util.Ids;
import com.easytest.common.web.ApiException;
import com.easytest.core.feature.project.ProjectDtos.MemberRequest;
import com.easytest.core.feature.project.ProjectDtos.MemberView;
import com.easytest.core.feature.project.ProjectDtos.ProjectDetail;
import com.easytest.core.feature.project.ProjectDtos.ProjectRequest;
import com.easytest.core.feature.project.ProjectDtos.ProjectView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members) {
        this.projects = projects;
        this.members = members;
    }

    public List<ProjectView> list() {
        var principal = CurrentUser.get();
        if (principal == null || principal.role() == com.easytest.common.security.Role.ADMIN) {
            return projects.findAll().stream().map(this::view).toList();
        }
        return members.findByUsernameOrderByCreatedAtAsc(principal.username()).stream()
                .map(member -> projects.findById(member.getProjectId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(this::view).toList();
    }

    public ProjectDetail detail(String id) {
        Project project = require(id);
        requireMembership(id);
        return new ProjectDetail(view(project), members.findByProjectIdOrderByCreatedAtAsc(id).stream()
                .map(member -> new MemberView(member.getId(), member.getUsername(), member.getRole(),
                        member.getCreatedAt())).toList());
    }

    @Transactional
    public ProjectView create(ProjectRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("项目名称不能为空");
        }
        Instant now = Instant.now();
        String id = Ids.newId("project");
        Project project = projects.save(new Project(id, request.name().trim(), request.key(),
                request.description(), CurrentUser.require().username(), now, now));
        members.save(new ProjectMember(Ids.newId("pm"), id, CurrentUser.require().username(), "ADMIN", now));
        return view(project);
    }

    @Transactional
    public ProjectView update(String id, ProjectRequest request) {
        Project project = require(id);
        requireMembership(id);
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("项目名称不能为空");
        }
        project.setName(request.name().trim());
        project.setKey(request.key());
        project.setDescription(request.description());
        project.setUpdatedAt(Instant.now());
        return view(projects.save(project));
    }

    @Transactional
    public void delete(String id) {
        if (ProjectContext.DEFAULT_PROJECT_ID.equals(id)) {
            throw ApiException.badRequest("默认项目不能删除");
        }
        requireMembership(id);
        projects.delete(require(id));
    }

    @Transactional
    public List<MemberView> members(String projectId) {
        require(projectId);
        requireMembership(projectId);
        return members.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(member -> new MemberView(member.getId(), member.getUsername(), member.getRole(),
                        member.getCreatedAt())).toList();
    }

    @Transactional
    public MemberView addMember(String projectId, MemberRequest request) {
        require(projectId);
        requireMembership(projectId);
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw ApiException.badRequest("username 不能为空");
        }
        if (members.existsByProjectIdAndUsername(projectId, request.username().trim())) {
            throw ApiException.conflict("成员已存在");
        }
        ProjectMember member = members.save(new ProjectMember(Ids.newId("pm"), projectId,
                request.username().trim(), request.role() == null ? "TESTER" : request.role(), Instant.now()));
        return new MemberView(member.getId(), member.getUsername(), member.getRole(), member.getCreatedAt());
    }

    @Transactional
    public void removeMember(String projectId, String memberId) {
        requireMembership(projectId);
        ProjectMember member = members.findById(memberId)
                .filter(item -> projectId.equals(item.getProjectId()))
                .orElseThrow(() -> ApiException.notFound("成员不存在"));
        members.delete(member);
    }

    private Project require(String id) {
        return projects.findById(id).orElseThrow(() -> ApiException.notFound("项目不存在: " + id));
    }

    private void requireMembership(String projectId) {
        var principal = CurrentUser.get();
        if (principal != null && principal.role() != com.easytest.common.security.Role.ADMIN
                && !members.existsByProjectIdAndUsername(projectId, principal.username())) {
            throw ApiException.forbidden("无权访问项目: " + projectId);
        }
    }

    private ProjectView view(Project project) {
        return new ProjectView(project.getId(), project.getName(), project.getKey(), project.getDescription(),
                project.getCreatedBy(), project.getCreatedAt(), project.getUpdatedAt(),
                members.findByProjectIdOrderByCreatedAtAsc(project.getId()).size());
    }
}
