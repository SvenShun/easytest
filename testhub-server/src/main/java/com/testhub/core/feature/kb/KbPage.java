package com.testhub.core.feature.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A screen/page inside an app. */
@Entity
@Table(name = "kb_page")
public class KbPage {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "route", length = 500)
    private String route;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "screenshot_url", length = 1000)
    private String screenshotUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbPage() {
    }

    public KbPage(String id, String appId, String name, String route, String description,
                  String screenshotUrl, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.appId = appId;
        this.name = name;
        this.route = route;
        this.description = description;
        this.screenshotUrl = screenshotUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getAppId() { return appId; }
    public String getName() { return name; }
    public String getRoute() { return route; }
    public String getDescription() { return description; }
    public String getScreenshotUrl() { return screenshotUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setRoute(String route) { this.route = route; }
    public void setDescription(String description) { this.description = description; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
