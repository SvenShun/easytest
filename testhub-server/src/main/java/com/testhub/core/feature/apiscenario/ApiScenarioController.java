package com.testhub.core.feature.apiscenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.common.security.RequireRole;
import com.testhub.common.security.Role;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.apiscenario.ApiScenarioDtos.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/api-scenarios")
@RequireRole({Role.ADMIN, Role.TESTER, Role.BUSINESS, Role.OPS})
public class ApiScenarioController {

    private final ApiScenarioService service;

    public ApiScenarioController(ApiScenarioService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ScenarioView> list(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return service.list(keyword, page, size);
    }

    @GetMapping("/{id}")
    public ScenarioView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ScenarioView create(@RequestBody ScenarioRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ScenarioView update(@PathVariable String id, @RequestBody ScenarioRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @PostMapping("/validate")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public JsonNode validate(@RequestBody ValidateRequest request) {
        return service.validate(request == null ? null : request.yaml());
    }

    @PostMapping("/{id}/run")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public RunView run(@PathVariable String id, @RequestBody(required = false) RunRequest request) {
        return service.run(id, request);
    }

    @GetMapping("/runs/{runId}")
    public RunView runDetail(@PathVariable String runId) {
        return service.getRun(runId);
    }

    @PostMapping("/batch-run")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public BatchRunView batchRun(@RequestBody BatchRunRequest request) {
        return service.batchRun(request);
    }

    @GetMapping("/{id}/export")
    public ExportView export(@PathVariable String id) {
        return service.export(id);
    }

    @PostMapping("/import")
    @RequireRole({Role.ADMIN, Role.TESTER})
    public ScenarioView importScenario(@RequestBody ImportRequest request) {
        return service.importScenario(request);
    }

    @GetMapping("/{id}/runs")
    public PageResponse<RunView> runs(@PathVariable String id,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        return service.runs(id, page, size);
    }
}
