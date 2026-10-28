package com.testhub.core.feature.notification;

import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record ChannelRequest(String name, String channelType, String webhookUrl, String secret,
                                 Boolean enabled, String events) {
    }

    public record ChannelView(String id, String name, String channelType, String webhookUrl,
                              boolean hasSecret, boolean enabled, String events, String createdBy,
                              Instant createdAt, Instant updatedAt, Instant lastTestAt,
                              String lastTestStatus) {
    }

    public record TestResult(boolean ok, String message, Instant sentAt) {
    }
}
