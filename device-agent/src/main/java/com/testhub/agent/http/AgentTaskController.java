package com.testhub.agent.http;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.testhub.agent.dispatch.AgentTask;
import com.testhub.agent.dispatch.TaskDispatchRequest;
import com.testhub.agent.dispatch.TaskDispatcher;

/**
 * Dispatch protocol consumed by ui-execution (contract §2.6 / §3).
 */
@RestController
@RequestMapping("/agent/tasks")
public class AgentTaskController {

    private final TaskDispatcher dispatcher;

    public AgentTaskController(TaskDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping
    public ResponseEntity<Object> dispatch(@RequestBody TaskDispatchRequest request) {
        if (request == null || request.taskId() == null || request.taskId().isBlank()) {
            return ResponseEntity.badRequest().body(ApiError.of(400, "INVALID_TASK_ID",
                    "taskId is required", "/agent/tasks"));
        }
        String executorType = request.executorType();
        if (!TaskDispatchRequest.EXECUTOR_SCRIPT.equals(executorType)
                && !TaskDispatchRequest.EXECUTOR_AI_RUNTIME.equals(executorType)) {
            return ResponseEntity.badRequest().body(ApiError.of(400, "UNKNOWN_EXECUTOR",
                    "unknown executorType: " + executorType, "/agent/tasks"));
        }
        AgentTask task = dispatcher.dispatch(request);
        if (task == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409,
                    "TASK_ALREADY_RUNNING",
                    "task " + request.taskId() + " is already known to this agent",
                    "/agent/tasks"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accepted", true);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Object> cancel(@PathVariable String taskId) {
        dispatcher.cancel(taskId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelling", true);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<Object> status(@PathVariable String taskId) {
        AgentTask task = dispatcher.registry().get(taskId);
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(404,
                    "TASK_NOT_FOUND", "task not found: " + taskId, "/agent/tasks/" + taskId));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", task.getTaskId());
        body.put("status", task.getStatus());
        body.put("executorType", task.getExecutorType());
        return ResponseEntity.ok(body);
    }
}
