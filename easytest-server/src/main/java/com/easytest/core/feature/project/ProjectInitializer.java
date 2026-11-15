package com.easytest.core.feature.project;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Seeds the default project and gives seeded users membership. */
@Component
@Order(20)
public class ProjectInitializer implements ApplicationRunner {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;

    public ProjectInitializer(ProjectRepository projects, ProjectMemberRepository members) {
        this.projects = projects;
        this.members = members;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Project project = projects.findById(ProjectContext.DEFAULT_PROJECT_ID).orElseGet(() ->
                projects.save(new Project(ProjectContext.DEFAULT_PROJECT_ID, "默认项目", "default",
                        "EasyTest 默认项目", "system", Instant.now(), Instant.now())));
        for (String username : List.of("admin", "tester", "business", "ops")) {
            if (!members.existsByProjectIdAndUsername(project.getId(), username)) {
                members.save(new ProjectMember("pm-default-" + username, project.getId(), username, "TESTER",
                        Instant.now()));
            }
        }
    }
}
