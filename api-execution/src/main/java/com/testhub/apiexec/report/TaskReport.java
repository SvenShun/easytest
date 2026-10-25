package com.testhub.apiexec.report;

import com.testhub.apiexec.assertion.AssertionResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** JSON document returned by {@code GET .../tasks/{id}/report} (contract §5). */
public record TaskReport(String taskId, String caseId, String caseName, String status,
                         String workerId, String failureReason, RequestView request,
                         ResponseView response, List<AssertionResult> assertions, String summary,
                         Instant createdAt, Instant startedAt, Instant finishedAt) {

    public record RequestView(String method, String url, Map<String, String> headers,
                              Map<String, String> query, String body, int timeoutSec) {
    }

    public record ResponseView(Integer status, Map<String, List<String>> headers,
                               String body, Long durationMs) {
    }
}
