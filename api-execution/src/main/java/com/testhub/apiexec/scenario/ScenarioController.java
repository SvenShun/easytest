package com.testhub.apiexec.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/api-execution/scenarios")
public class ScenarioController {

    private final ScenarioExecutionService service;

    public ScenarioController(ScenarioExecutionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ScenarioDtos.CreateScenarioResponse create(@RequestBody ScenarioDtos.CreateScenarioRequest request) {
        return service.create(request, CurrentUser.require().username());
    }

    @PostMapping("/validate")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ScenarioDtos.ValidateResponse validate(@RequestBody Map<String, String> body) {
        return service.validate(body == null ? null : body.get("yaml"));
    }

    @GetMapping("/{taskId}")
    public ScenarioDtos.ScenarioTaskView detail(@PathVariable String taskId) {
        return service.detail(taskId);
    }

    @GetMapping("/{taskId}/report")
    public JsonNode report(@PathVariable String taskId) {
        return service.report(taskId);
    }

    @PostMapping("/{taskId}/cancel")
    public ScenarioDtos.CancelScenarioResponse cancel(@PathVariable String taskId) {
        return service.cancel(taskId);
    }
}
