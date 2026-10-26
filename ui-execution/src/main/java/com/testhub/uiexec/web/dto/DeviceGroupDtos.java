package com.testhub.uiexec.web.dto;

import java.time.Instant;
import java.util.List;

public final class DeviceGroupDtos {

    private DeviceGroupDtos() {
    }

    public record GroupRequest(String name, String description, List<String> deviceIds) {
    }

    public record GroupView(String id, String name, String description, List<String> deviceIds,
                            int deviceCount, Instant createdAt, Instant updatedAt) {
    }
}
