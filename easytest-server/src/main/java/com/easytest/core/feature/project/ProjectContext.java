package com.easytest.core.feature.project;

import com.easytest.common.security.AuthPrincipal;
import com.easytest.common.security.CurrentUser;
import com.easytest.common.security.Role;
import com.easytest.common.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ProjectContext {

    public static final String HEADER = "X-Project-Id";
    public static final String DEFAULT_PROJECT_ID = "default";

    private static final ThreadLocal<String> OVERRIDE = new ThreadLocal<>();

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;

    public ProjectContext(ProjectRepository projects, ProjectMemberRepository members) {
        this.projects = projects;
        this.members = members;
    }

    /** Runs scheduler/background work under a project without an HTTP request. */
    public <T> T runAs(String projectId, java.util.function.Supplier<T> action) {
        String previous = OVERRIDE.get();
        OVERRIDE.set(projectId);
        try {
            return action.get();
        } finally {
            if (previous == null) OVERRIDE.remove(); else OVERRIDE.set(previous);
        }
    }

    public String currentProjectRole() {
        AuthPrincipal principal = CurrentUser.get();
        if (principal == null) return "TESTER";
        if (principal.role() == Role.ADMIN) return "ADMIN";
        return members.findByProjectIdAndUsername(currentProjectId(), principal.username())
                .map(ProjectMember::getRole).orElse(null);
    }

    public void requireProjectAdmin(String projectId) {
        AuthPrincipal principal = CurrentUser.get();
        if (principal != null && principal.role() == Role.ADMIN) return;
        String role = members.findByProjectIdAndUsername(projectId, principal == null ? "" : principal.username())
                .map(ProjectMember::getRole).orElse(null);
        if (!"ADMIN".equals(role)) {
            throw ApiException.forbidden("需要项目管理员权限");
        }
    }

    /** Header-selected project; falls back to the seeded default project. */
    public String currentProjectId() {
        String override = OVERRIDE.get();
        if (override != null) return override;
        String header = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            header = request.getHeader(HEADER);
        }
        String id = header == null || header.isBlank() ? DEFAULT_PROJECT_ID : header.trim();
        if (!projects.existsById(id)) {
            throw ApiException.notFound("项目不存在: " + id);
        }
        AuthPrincipal principal = CurrentUser.get();
        if (principal != null && principal.role() != Role.ADMIN
                && !members.existsByProjectIdAndUsername(id, principal.username())) {
            throw ApiException.forbidden("无权访问项目: " + id);
        }
        return id;
    }
}
