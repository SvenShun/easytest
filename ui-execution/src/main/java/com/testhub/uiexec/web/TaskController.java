package com.testhub.uiexec.web;

import com.testhub.common.security.AuthFilter;
import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.uiexec.domain.TaskEntity;
import com.testhub.uiexec.domain.TaskStatus;
import com.testhub.uiexec.service.AgentTokenGuard;
import com.testhub.uiexec.service.LogService;
import com.testhub.uiexec.service.StepService;
import com.testhub.uiexec.service.TaskService;
import com.testhub.uiexec.service.TaskStreamService;
import com.testhub.uiexec.web.dto.TaskDtos.CancelTaskResponse;
import com.testhub.uiexec.web.dto.TaskDtos.CreateTaskRequest;
import com.testhub.uiexec.web.dto.TaskDtos.CreateTaskResponse;
import com.testhub.uiexec.web.dto.TaskDtos.LogBatchRequest;
import com.testhub.uiexec.web.dto.TaskDtos.LogView;
import com.testhub.uiexec.web.dto.TaskDtos.StepCallbackRequest;
import com.testhub.uiexec.web.dto.TaskDtos.StepCallbackResponse;
import com.testhub.uiexec.web.dto.TaskDtos.StepView;
import com.testhub.uiexec.web.dto.TaskDtos.TaskDetailView;
import com.testhub.uiexec.web.dto.TaskDtos.TaskResultRequest;
import com.testhub.uiexec.web.dto.TaskDtos.TaskResultResponse;
import com.testhub.uiexec.web.dto.TaskDtos.TaskSummaryView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** Task APIs (docs/API-CONTRACT.md §2.1–2.3). */
@RestController
@RequestMapping("/api/v1/ui-execution/tasks")
public class TaskController {

    private final TaskService taskService;
    private final StepService stepService;
    private final LogService logService;
    private final TaskStreamService taskStreamService;
    private final AgentTokenGuard agentTokenGuard;

    public TaskController(TaskService taskService, StepService stepService, LogService logService,
                          TaskStreamService taskStreamService, AgentTokenGuard agentTokenGuard) {
        this.taskService = taskService;
        this.stepService = stepService;
        this.logService = logService;
        this.taskStreamService = taskStreamService;
        this.agentTokenGuard = agentTokenGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRole({Role.ADMIN, Role.TESTER})
    public CreateTaskResponse create(@RequestBody CreateTaskRequest request) {
        AuthPrincipal principal = CurrentUser.require();
        TaskEntity task = taskService.create(request, principal.username());
        return new CreateTaskResponse(task.getId(), task.getStatus(), task.getExecutorType(),
                task.getDeviceId(), task.getDeviceSerial(), taskService.queuePosition(task),
                task.getCreatedAt());
    }

    @GetMapping
    public PageResponse<TaskSummaryView> list(
            @RequestParam(required = false) String planRunId,
            @RequestParam(required = false) String planId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String caseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return taskService.search(planRunId, planId, parseStatus(status), deviceId, caseId, page, size);
    }

    @GetMapping("/{taskId}")
    public TaskDetailView detail(@PathVariable String taskId) {
        return taskService.detail(taskId);
    }

    @GetMapping("/{taskId}/steps")
    public List<StepView> steps(@PathVariable String taskId) {
        return taskService.listSteps(taskId);
    }

    @GetMapping("/{taskId}/logs")
    public List<LogView> logs(@PathVariable String taskId,
                              @RequestParam(defaultValue = "0") int afterSeq) {
        return taskService.listLogs(taskId, afterSeq);
    }

    @GetMapping(path = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String taskId) {
        AuthPrincipal principal = CurrentUser.require();
        // Contract: the SSE stream is a UI concern and must be opened with a JWT (service token is also fine).
        if ("agent".equals(principal.userId())) {
            throw ApiException.unauthorized("SSE 事件流需要使用用户 JWT 访问");
        }
        return taskStreamService.open(taskId);
    }

    @PostMapping("/{taskId}/cancel")
    public CancelTaskResponse cancel(@PathVariable String taskId, HttpServletRequest request) {
        AuthPrincipal principal = CurrentUser.require();
        if (request.getHeader(AuthFilter.AGENT_TOKEN_HEADER) != null) {
            agentTokenGuard.require(request);
        } else if (principal.role() != Role.ADMIN && principal.role() != Role.TESTER) {
            throw ApiException.forbidden("当前角色无权取消任务");
        }
        TaskEntity task = taskService.cancel(taskId);
        return new CancelTaskResponse(task.getId(), task.getStatus());
    }

    // ------------------------------------------------------ agent callbacks

    @PostMapping("/{taskId}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public StepCallbackResponse reportStep(@PathVariable String taskId,
                                           @RequestBody StepCallbackRequest request,
                                           HttpServletRequest httpRequest) {
        agentTokenGuard.require(httpRequest);
        return stepService.upsert(taskId, request);
    }

    @PostMapping("/{taskId}/logs")
    public ResponseEntity<Void> reportLogs(@PathVariable String taskId,
                                           @RequestBody LogBatchRequest request,
                                           HttpServletRequest httpRequest) {
        agentTokenGuard.require(httpRequest);
        taskService.requireTask(taskId);
        logService.append(taskId, request == null ? null : request.lines());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{taskId}/result")
    public TaskResultResponse reportResult(@PathVariable String taskId,
                                           @RequestBody TaskResultRequest request,
                                           HttpServletRequest httpRequest) {
        agentTokenGuard.require(httpRequest);
        TaskEntity task = taskService.onResult(taskId, request);
        return new TaskResultResponse(task.getId(), task.getStatus(), task.getFailureReportUrl());
    }

    private TaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TaskStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("非法的任务状态: " + status);
        }
    }
}
