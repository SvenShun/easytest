package com.testhub.apiexec.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.apiexec.assertion.AssertionResult;
import com.testhub.apiexec.domain.ApiTaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Renders the persisted task as the unified API execution report. */
@Component
public class TaskReportFactory {

    private static final Logger log = LoggerFactory.getLogger(TaskReportFactory.class);

    private final ObjectMapper objectMapper;

    public TaskReportFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TaskReport build(ApiTaskEntity task) {
        TaskReport.ResponseView response = task.getHttpStatus() == null && task.getResponseBody() == null
                ? null
                : new TaskReport.ResponseView(task.getHttpStatus(), readHeaders(task.getResponseHeaders()),
                task.getResponseBody(), task.getDurationMs());
        return new TaskReport(
                task.getId(),
                task.getCaseId(),
                task.getCaseName(),
                task.getStatus() == null ? null : task.getStatus().name(),
                task.getWorkerId(),
                task.getFailureReason(),
                new TaskReport.RequestView(task.getMethod(), task.getUrl(), readStringMap(task.getHeadersJson()),
                        readStringMap(task.getQueryJson()), task.getBody(), task.getTimeoutSec()),
                response,
                readAssertionResults(task.getAssertionResultsJson()),
                task.getSummary(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getFinishedAt());
    }

    /** Serializes the report; a serialization failure must not abort task completion. */
    public String toJson(ApiTaskEntity task) {
        try {
            return objectMapper.writeValueAsString(build(task));
        } catch (Exception e) {
            log.warn("生成任务报告失败 taskId={}: {}", task.getId(), e.getMessage());
            return null;
        }
    }

    public List<AssertionResult> readAssertionResults(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AssertionResult>>() {
            });
        } catch (Exception e) {
            log.warn("解析断言结果失败: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, List<String>> readHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, List<String>>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
