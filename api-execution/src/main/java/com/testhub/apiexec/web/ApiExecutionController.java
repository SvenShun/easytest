package com.testhub.apiexec.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.apiexec.domain.ApiTaskStatus;
import com.testhub.apiexec.service.ApiExecutionService;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.CancelTaskResponse;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.CreateTaskRequest;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.CreateTaskResponse;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.TaskDetailView;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.TaskStatsView;
import com.testhub.apiexec.web.dto.ApiExecutionDtos.TaskSummaryView;
import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * API execution endpoints (docs/API-EXECUTION-CONTRACT.md §3).
 *
 * <p>Task creation needs TESTER/ADMIN (the service principal carries ADMIN, so
 * the service token is allowed); every read/cancel endpoint accepts any
 * authenticated JWT role or the service token via the common default-deny
 * interceptor.</p>
 */
@RestController
@RequestMapping("/api/v1/api-execution")
public class ApiExecutionController {

    private final ApiExecutionService executionService;

    public ApiExecutionController(ApiExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRole({Role.ADMIN, Role.TESTER})
    public CreateTaskResponse create(@RequestBody CreateTaskRequest request) {
        AuthPrincipal principal = CurrentUser.require();
        return executionService.create(request, principal.username());
    }

    @GetMapping("/tasks")
    public PageResponse<TaskSummaryView> list(
            @RequestParam(required = false) String planRunId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireUserOrService();
        return executionService.search(planRunId, parseStatus(status), page, size);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskDetailView detail(@PathVariable String taskId) {
        requireUserOrService();
        return executionService.detail(taskId);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public CancelTaskResponse cancel(@PathVariable String taskId) {
        requireUserOrService();
        return executionService.cancel(taskId);
    }

    @GetMapping("/tasks/{taskId}/report")
    public JsonNode report(@PathVariable String taskId) {
        requireUserOrService();
        return executionService.report(taskId);
    }

    @GetMapping("/stats")
    public TaskStatsView stats() {
        requireUserOrService();
        return executionService.stats();
    }

    /** The contract allows a JWT (any role) or the service token, not agent tokens. */
    private void requireUserOrService() {
        AuthPrincipal principal = CurrentUser.require();
        if ("agent".equals(principal.userId())) {
            throw ApiException.unauthorized("该接口需要用户 JWT 或服务令牌");
        }
    }

    private ApiTaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ApiTaskStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("非法的任务状态: " + status);
        }
    }
}
