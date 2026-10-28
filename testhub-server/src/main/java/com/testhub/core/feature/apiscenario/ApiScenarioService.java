package com.testhub.core.feature.apiscenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.apienv.ApiEnvironment;
import com.testhub.core.feature.apienv.ApiEnvironmentRepository;
import com.testhub.core.feature.apienv.ApiEnvironmentResolver;
import com.testhub.core.feature.apiscenario.ApiScenarioDtos.*;
import com.testhub.core.feature.support.PageSupport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ApiScenarioService {

    private final ApiScenarioRepository scenarios;
    private final ApiScenarioRunRepository runs;
    private final ApiEnvironmentRepository environments;
    private final ApiEnvironmentResolver environmentResolver;
    private final ScenarioExecutionClient executionClient;
    private final ObjectMapper objectMapper;

    public ApiScenarioService(ApiScenarioRepository scenarios, ApiScenarioRunRepository runs,
                              ApiEnvironmentRepository environments, ApiEnvironmentResolver environmentResolver,
                              ScenarioExecutionClient executionClient, ObjectMapper objectMapper) {
        this.scenarios = scenarios;
        this.runs = runs;
        this.environments = environments;
        this.environmentResolver = environmentResolver;
        this.executionClient = executionClient;
        this.objectMapper = objectMapper;
    }

    public PageResponse<ScenarioView> list(String keyword, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<ApiScenario> result = scenarios.findAll(spec(keyword), pageRequest);
        return PageSupport.of(result, this::view);
    }

    public ScenarioView get(String id) {
        return view(require(id));
    }

    @Transactional
    public ScenarioView create(ScenarioRequest request) {
        validate(request);
        Instant now = Instant.now();
        ApiScenario scenario = new ApiScenario(Ids.newId("scenario"), request.name().trim(),
                blank(request.description()), request.yaml(), blank(request.environmentId()),
                request.enabled() == null || request.enabled(), CurrentUser.require().username(), now, now);
        return view(scenarios.save(scenario));
    }

    @Transactional
    public ScenarioView update(String id, ScenarioRequest request) {
        validate(request);
        ApiScenario scenario = require(id);
        scenario.setName(request.name().trim());
        scenario.setDescription(blank(request.description()));
        scenario.setYamlText(request.yaml());
        scenario.setEnvironmentId(blank(request.environmentId()));
        scenario.setEnabled(request.enabled() == null || request.enabled());
        scenario.setUpdatedAt(Instant.now());
        return view(scenarios.save(scenario));
    }

    @Transactional
    public void delete(String id) {
        scenarios.delete(require(id));
    }

    public JsonNode validate(String yaml) {
        return executionClient.validate(yaml);
    }

    public RunView run(String scenarioId, RunRequest request) {
        String envId = request != null && request.environmentId() != null && !request.environmentId().isBlank()
                ? request.environmentId().trim() : null;
        Map<String, Object> variables = request == null || request.variables() == null
                ? Map.of() : request.variables();
        int timeout = request == null || request.timeoutSec() == null || request.timeoutSec() <= 0
                ? 120 : Math.min(request.timeoutSec(), 3600);
        RunHandle handle = start(scenarioId, envId, variables, timeout, CurrentUser.require().username(),
                null, null, null);
        return getRun(handle.runId());
    }

    /** Starts a scenario for a plan item; the scenario task reports back with planRunId/caseId. */
    public RunHandle startForPlan(String scenarioId, String planId, String planRunId, int timeout, String actor) {
        return start(scenarioId, null, Map.of(),
                Math.max(1, Math.min(timeout, 3600)), actor, planId, planRunId, scenarioId);
    }

    private RunHandle start(String scenarioId, String envOverride, Map<String, Object> variables, int timeout,
                            String actor, String planId, String planRunId, String caseId) {
        ApiScenario scenario = require(scenarioId);
        if (!scenario.isEnabled()) {
            throw ApiException.conflict("场景已禁用，不能运行");
        }
        JsonNode validation = executionClient.validate(scenario.getYamlText());
        if (validation != null && validation.has("valid") && !validation.get("valid").asBoolean()) {
            throw ApiException.badRequest(validation.path("message").asText("YAML 校验失败"));
        }
        String envId = envOverride == null || envOverride.isBlank() ? scenario.getEnvironmentId() : envOverride;
        Instant now = Instant.now();
        ApiScenarioRun run = new ApiScenarioRun(Ids.newId("scenario-run"), scenario.getId(), scenario.getName(),
                blank(envId), json(variables), actor, now);
        run = runs.save(run);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scenarioId", scenario.getId());
            payload.put("runId", run.getId());
            payload.put("name", scenario.getName());
            payload.put("yaml", scenario.getYamlText());
            payload.put("environment", environment(envId));
            payload.put("variables", variables);
            payload.put("timeoutSec", timeout);
            payload.put("createdBy", actor);
            if (planId != null) payload.put("planId", planId);
            if (planRunId != null) payload.put("planRunId", planRunId);
            if (caseId != null) payload.put("caseId", caseId);
            JsonNode created = executionClient.create(payload);
            run.setTaskId(text(created, "taskId"));
            run.setStatus(textOrDefault(created, "status", "PENDING"));
            runs.save(run);
            return new RunHandle(run.getId(), run.getTaskId(), run.getStatus(), scenario.getName());
        } catch (RuntimeException e) {
            run.setStatus("FAILED");
            run.setFailureReason("EXECUTION_UNAVAILABLE");
            run.setSummary(e.getMessage());
            run.setFinishedAt(Instant.now());
            runs.save(run);
            throw e;
        }
    }

    public RunView getRun(String runId) {
        ApiScenarioRun run = requireRun(runId);
        if (run.getTaskId() != null && !terminal(run.getStatus())) {
            try {
                JsonNode detail = executionClient.detail(run.getTaskId());
                if (detail != null) {
                    String status = textOrDefault(detail, "status", run.getStatus());
                    run.setStatus(status);
                    run.setStepsJson(detail.has("steps") && !detail.get("steps").isNull()
                            ? detail.get("steps").toString() : null);
                    run.setReportJson(detail.has("report") && !detail.get("report").isNull()
                            ? detail.get("report").toString() : null);
                    run.setSummary(text(detail, "summary"));
                    run.setFailureReason(text(detail, "failureReason"));
                    if (detail.has("durationMs") && !detail.get("durationMs").isNull()) {
                        run.setDurationMs(detail.get("durationMs").asLong());
                    }
                    if (terminal(status)) {
                        run.setFinishedAt(Instant.now());
                    }
                    runs.save(run);
                }
            } catch (RuntimeException ignored) {
                // Keep the last known state; the report page can retry.
            }
        }
        return runView(run);
    }

    public record RunHandle(String runId, String taskId, String status, String scenarioName) {
    }

    public PageResponse<RunView> runs(String scenarioId, Integer page, Integer size) {
        require(scenarioId);
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ApiScenarioRun> result = runs.findByScenarioId(scenarioId, pageRequest);
        return PageSupport.of(result, this::runView);
    }

    private Map<String, Object> environment(String environmentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (environmentId == null || environmentId.isBlank()) {
            return Map.of();
        }
        ApiEnvironment environment = environments.findById(environmentId).orElse(null);
        if (environment == null) {
            throw ApiException.notFound("API 环境不存在: " + environmentId);
        }
        ApiEnvironmentResolver.ResolvedEnvironment resolved = environmentResolver.resolve(environment);
        result.put("baseUrl", resolved.baseUrl());
        result.put("headers", resolved.headers());
        result.put("variables", resolved.variables());
        return result;
    }

    private void validate(ScenarioRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("场景名称不能为空");
        }
        if (request.yaml() == null || request.yaml().isBlank()) {
            throw ApiException.badRequest("场景 YAML 不能为空");
        }
        if (!request.yaml().contains("steps:")) {
            throw ApiException.badRequest("场景 YAML 必须包含 steps 数组");
        }
    }

    private ApiScenario require(String id) {
        return scenarios.findById(id).orElseThrow(() -> ApiException.notFound("场景不存在: " + id));
    }

    private ApiScenarioRun requireRun(String id) {
        return runs.findById(id).orElseThrow(() -> ApiException.notFound("场景运行不存在: " + id));
    }

    private ScenarioView view(ApiScenario scenario) {
        return new ScenarioView(scenario.getId(), scenario.getName(), scenario.getDescription(),
                scenario.getYamlText(), scenario.getEnvironmentId(), scenario.isEnabled(),
                scenario.getCreatedBy(), scenario.getCreatedAt(), scenario.getUpdatedAt());
    }

    private RunView runView(ApiScenarioRun run) {
        return new RunView(run.getId(), run.getScenarioId(), run.getScenarioName(), run.getTaskId(),
                run.getStatus(), run.getEnvironmentId(), run.getSummary(), run.getFailureReason(),
                run.getDurationMs(), readJson(run.getStepsJson()), readJson(run.getReportJson()),
                readMap(run.getVariablesJson()), run.getCreatedBy(), run.getCreatedAt(), run.getFinishedAt());
    }

    private Specification<ApiScenario> spec(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            predicates.add(cb.like(cb.lower(root.get("description")), pattern));
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean terminal(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status) || "TIMEOUT".equals(status)
                || "CANCELLED".equals(status);
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private String textOrDefault(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null ? fallback : value;
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
