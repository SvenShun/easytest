package com.easytest.core.feature.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "project_member", uniqueConstraints = @UniqueConstraint(
        name = "uk_project_member", columnNames = {"project_id", "username"}))
public class ProjectMember {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "project_id", length = 64, nullable = false)
    private String projectId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "member_role", length = 16, nullable = false)
    private String role = "TESTER";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectMember() {
    }

    public ProjectMember(String id, String projectId, String username, String role, Instant createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.username = username;
        this.role = role;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
