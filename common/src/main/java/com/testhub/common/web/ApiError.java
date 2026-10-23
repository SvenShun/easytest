package com.testhub.common.web;

import java.time.Instant;
import java.util.List;

/** Error body returned by every TestHub service. */
public record ApiError(Instant timestamp, int status, String code, String message, String path, List<String> details) {

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, List.of());
    }
}
