package com.testhub.apiexec.web.dto;

import com.testhub.apiexec.assertion.ApiAssertion;
import com.testhub.apiexec.assertion.AssertionResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response DTOs for the api-execution HTTP API. */
public final class ApiExecutionDtos {

    private ApiExecutionDtos() {
    }

    /** Contract §3.1 payload. */
    public record CreateTaskRequest(String planId, String planRunId, String caseId, String caseName,
                                    RequestPayload request, List<ApiAssertion> assertions,
                                    String createdBy) {
    }

    public record RequestPayload(String method, String baseUrl, String path,
                                 Map<String, String> headers, Map<String, String> query,
                                 String body, Integer timeoutSec) {
    }

    public record CreateTaskResponse(String taskId, String status) {
    }

    public record CancelTaskResponse(String taskId, String status) {
    }

    public record RequestView(String method, String url, Map<String, String> headers,
                              Map<String, String> query, String body, int timeoutSec) {
    }

    public record ResponseView(Integer status, Map<String, List<String>> headers,
                               String body, Long durationMs) {
    }

    public record TaskSummaryView(String taskId, String planId, String planRunId, String caseId,
                                  String caseName, String method, String url, String status,
                                  Integer httpStatus, Long durationMs, String failureReason,
                                  String summary, String failureReportUrl, String createdBy,
                                  Instant createdAt, Instant startedAt, Instant finishedAt) {
    }

    public record TaskDetailView(String taskId, String planId, String planRunId, String caseId,
                                 String caseName, String status, String failureReason, String summary,
                                 RequestView request, ResponseView response,
                                 List<AssertionResult> assertionResults, Integer httpStatus,
                                 Long durationMs, String failureReportUrl, String createdBy,
                                 Instant createdAt, Instant startedAt, Instant finishedAt) {
    }

    /** Contract §3.2 stats envelope. */
    public record TaskStatsView(long pending, long running, long success, long failed, long timeout,
                                long cancelled, int workers) {
    }
}
