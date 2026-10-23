package com.testhub.agent.http;

import java.time.Instant;
import java.util.List;

/**
 * Error body aligned with the cross-service contract:
 * {@code {timestamp,status,code,message,path,details[]}}.
 */
public record ApiError(
        String timestamp,
        int status,
        String code,
        String message,
        String path,
        List<String> details) {

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now().toString(), status, code, message, path, List.of());
    }
}
