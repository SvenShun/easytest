package com.easytest.uiexec.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Project selected by the core UI (X-Project-Id); defaults to the platform default project. */
@Component
public class ProjectScope {

    public static final String DEFAULT_PROJECT_ID = "default";

    public String currentProjectId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String header = request.getHeader("X-Project-Id");
            if (header != null && !header.isBlank()) return header.trim();
        }
        return DEFAULT_PROJECT_ID;
    }
}
