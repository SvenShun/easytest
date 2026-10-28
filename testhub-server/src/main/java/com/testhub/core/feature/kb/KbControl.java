package com.testhub.core.feature.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A control/element on a page, with locator, intent and screenshot. */
@Entity
@Table(name = "kb_control")
public class KbControl {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "page_id", nullable = false, length = 64)
    private String pageId;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "text", length = 1000)
    private String text;

    @Column(name = "resource_id", length = 500)
    private String resourceId;

    @Column(name = "description", length = 2000)
    private String description;

    /** Non-visible locator description (Sonic element desc). */
    @Column(name = "element_desc", length = 1000)
    private String desc;

    @Column(name = "intent", length = 1000)
    private String intent;

    @Column(name = "control_type", length = 40)
    private String controlType;

    @Column(name = "screenshot_url", length = 1000)
    private String screenshotUrl;

    @Column(name = "bounds", length = 200)
    private String bounds;

    /** MANUAL | AI | CRAWLER. */
    @Column(name = "source", length = 16)
    private String source = "MANUAL";

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbControl() {
    }

    public KbControl(String id, String pageId, String name, String text, String resourceId, String description,
                     String desc, String intent, String controlType, String screenshotUrl, String bounds,
                     String source, Double confidence, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.pageId = pageId;
        this.name = name;
        this.text = text;
        this.resourceId = resourceId;
        this.description = description;
        this.desc = desc;
        this.intent = intent;
        this.controlType = controlType;
        this.screenshotUrl = screenshotUrl;
        this.bounds = bounds;
        this.source = source == null ? "MANUAL" : source;
        this.confidence = confidence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getPageId() { return pageId; }
    public String getName() { return name; }
    public String getText() { return text; }
    public String getResourceId() { return resourceId; }
    public String getDescription() { return description; }
    public String getDesc() { return desc; }
    public String getIntent() { return intent; }
    public String getControlType() { return controlType; }
    public String getScreenshotUrl() { return screenshotUrl; }
    public String getBounds() { return bounds; }
    public String getSource() { return source; }
    public Double getConfidence() { return confidence; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setText(String text) { this.text = text; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public void setDescription(String description) { this.description = description; }
    public void setDesc(String desc) { this.desc = desc; }
    public void setIntent(String intent) { this.intent = intent; }
    public void setControlType(String controlType) { this.controlType = controlType; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }
    public void setBounds(String bounds) { this.bounds = bounds; }
    public void setSource(String source) { this.source = source; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
