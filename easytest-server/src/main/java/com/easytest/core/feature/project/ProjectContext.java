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

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;

    public ProjectContext(ProjectRepository projects, ProjectMemberRepository members) {
        this.projects = projects;
        this.members = members;
    }

    /** Header-selected project; falls back to the seeded default project. */
    public String currentProjectId() {
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
