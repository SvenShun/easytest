package com.testhub.core.feature.kb;

import java.time.Instant;
import java.util.List;

public final class KbDtos {

    private KbDtos() {
    }

    public record AppRequest(String name, String packageName, String platform, String description, String iconUrl) {
    }

    public record AppView(String id, String name, String packageName, String platform, String description,
                          String iconUrl, long pageCount, long controlCount, String createdBy,
                          Instant createdAt, Instant updatedAt) {
    }

    public record PageRequest(String name, String route, String description, String screenshotUrl) {
    }

    public record PageView(String id, String appId, String name, String route, String description,
                           String screenshotUrl, long controlCount, Instant createdAt, Instant updatedAt) {
    }

    public record ControlRequest(String name, String text, String resourceId, String description, String desc,
                                 String intent, String controlType, String screenshotUrl, String bounds,
                                 String source, Double confidence) {
    }

    public record ControlView(String id, String pageId, String name, String text, String resourceId,
                              String description, String desc, String intent, String controlType,
                              String screenshotUrl, String bounds, String source, Double confidence,
                              List<ActionView> actions, Instant createdAt, Instant updatedAt) {
    }

    public record ActionRequest(String actionType, String label, String params, String source, Boolean enabled) {
    }

    public record ActionView(String id, String controlId, String actionType, String label, String params,
                             String source, boolean enabled, Instant createdAt) {
    }

    public record BatchControlRequest(List<ControlRequest> controls) {
    }

    public record ScreenshotView(String url) {
    }
}
