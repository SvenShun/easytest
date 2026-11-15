package com.easytest.core.feature.project;

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

    public ProjectContext(ProjectRepository projects) {
        this.projects = projects;
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
        return id;
    }
}
