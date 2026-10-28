package com.testhub.core.feature.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A webhook robot notified when a plan run finishes. */
@Entity
@Table(name = "notification_channel")
public class NotificationChannel {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /** GENERIC | DINGTALK | WECOM | FEISHU. */
    @Column(name = "channel_type", length = 16, nullable = false)
    private String channelType;

    @Column(name = "webhook_url", length = 2000, nullable = false)
    private String webhookUrl;

    /** Optional DingTalk signing secret. */
    @Column(name = "secret", length = 500)
    private String secret;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Comma separated event names; currently RUN_COMPLETED. */
    @Column(name = "events", length = 200)
    private String events = "RUN_COMPLETED";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_status", length = 300)
    private String lastTestStatus;

    protected NotificationChannel() {
    }

    public NotificationChannel(String id, String name, String channelType, String webhookUrl, String secret,
                               boolean enabled, String events, String createdBy, Instant createdAt,
                               Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.channelType = channelType;
        this.webhookUrl = webhookUrl;
        this.secret = secret;
        this.enabled = enabled;
        this.events = events;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getChannelType() { return channelType; }
    public String getWebhookUrl() { return webhookUrl; }
    public String getSecret() { return secret; }
    public boolean isEnabled() { return enabled; }
    public String getEvents() { return events; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastTestAt() { return lastTestAt; }
    public String getLastTestStatus() { return lastTestStatus; }

    public void setName(String name) { this.name = name; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public void setSecret(String secret) { this.secret = secret; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setEvents(String events) { this.events = events; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setLastTestAt(Instant lastTestAt) { this.lastTestAt = lastTestAt; }
    public void setLastTestStatus(String lastTestStatus) { this.lastTestStatus = lastTestStatus; }
}
