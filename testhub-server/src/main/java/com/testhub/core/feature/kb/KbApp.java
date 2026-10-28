package com.testhub.core.feature.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An application in the UI knowledge base. */
@Entity
@Table(name = "kb_app")
public class KbApp {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "package_name", length = 300)
    private String packageName;

    @Column(name = "platform", length = 40)
    private String platform;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "icon_url", length = 1000)
    private String iconUrl;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbApp() {
    }

    public KbApp(String id, String name, String packageName, String platform, String description,
                 String iconUrl, String createdBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.packageName = packageName;
        this.platform = platform;
        this.description = description;
        this.iconUrl = iconUrl;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPackageName() { return packageName; }
    public String getPlatform() { return platform; }
    public String getDescription() { return description; }
    public String getIconUrl() { return iconUrl; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public void setPlatform(String platform) { this.platform = platform; }
    public void setDescription(String description) { this.description = description; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
