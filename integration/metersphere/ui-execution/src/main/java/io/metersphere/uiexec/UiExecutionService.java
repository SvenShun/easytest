package io.metersphere.uiexec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.plan.dto.request.TestPlanCaseRunRequest;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.system.dto.LogInsertModule;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.uiexec.UiExecutionDtos.CreateTaskRequest;
import io.metersphere.uiexec.UiExecutionDtos.ResultCallback;
import io.metersphere.uiexec.UiExecutionDtos.StepCallback;
import io.metersphere.uiexec.UiExecutionDtos.StepView;
import io.metersphere.uiexec.UiExecutionDtos.TaskView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI execution orchestration inside MeterSphere.
 *
 * <p>Reads the functional case through MeterSphere's own service, dispatches the
 * TestHub device-agent protocol, receives step/result callbacks and writes the
 * final result through {@link TestPlanFunctionalCaseService#run}. Task state is
 * stored in MeterSphere's database with Flyway-managed tables.</p>
 *
 * <p>Devices are serialized FIFO: a task created while the device is busy stays
 * {@code PENDING} (with a queue position) and is dispatched when the active task
 * reaches a terminal state. A watchdog marks stuck tasks {@code TIMEOUT}.</p>
 */
@Service
public class UiExecutionService {

    private static final Logger log = LoggerFactory.getLogger(UiExecutionService.class);
    private static final int WATCHDOG_GRACE_MS = 15_000;

    private final JdbcTemplate jdbc;
    private final FunctionalCaseService functionalCaseService;
    private final TestPlanFunctionalCaseService testPlanFunctionalCaseService;
    private final DeviceAgentClient deviceAgentClient;
    private final UiExecutionProperties properties;
    private final ObjectMapper objectMapper;

    public UiExecutionService(JdbcTemplate jdbc, FunctionalCaseService functionalCaseService,
                              TestPlanFunctionalCaseService testPlanFunctionalCaseService,
                              DeviceAgentClient deviceAgentClient, UiExecutionProperties properties,
                              ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.functionalCaseService = functionalCaseService;
        this.testPlanFunctionalCaseService = testPlanFunctionalCaseService;
        this.deviceAgentClient = deviceAgentClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- create

    @Transactional(rollbackFor = Exception.class)
    public synchronized TaskView create(CreateTaskRequest request, String userId, String projectIdFromSession) {
        if (request == null || isBlank(request.caseId())) {
            throw new IllegalArgumentException("caseId 不能为空");
        }
        FunctionalCaseDetailDTO detail = functionalCaseService.getFunctionalCaseDetail(
                request.caseId(), userId, false);
        if (detail == null) {
            throw new IllegalArgumentException("功能用例不存在: " + request.caseId());
        }
        String projectId = firstNonBlank(request.projectId(), detail.getProjectId(), projectIdFromSession);
        String planId = firstNonBlank(request.testPlanId(), "");
        String planCaseId = firstNonBlank(request.testPlanCaseId(), "");
        String serial = firstNonBlank(request.deviceSerial(), properties.getDefaultDeviceSerial());
        int totalSec = request.timeoutSec() == null ? properties.getTotalTimeoutSec() : request.timeoutSec();
        int stepSec = request.stepTimeoutSec() == null ? properties.getStepTimeoutSec() : request.stepTimeoutSec();

        String id = IDGenerator.nextStr();
        String title = detail.getName();
        String given = text(detail.getPrerequisite(), "已打开待测应用");
        String when = text(firstNonBlank(detail.getTextDescription(), detail.getSteps()), "执行用例");
        String then = text(detail.getExpectedResult(), "用例执行成功");
        boolean busy = activeCount(serial) > 0;
        String status = busy ? "PENDING" : "DISPATCHED";
        int position = busy ? pendingCount(serial) + 1 : 0;
        long now = System.currentTimeMillis();
        jdbc.update("""
                        INSERT INTO ui_execution_task
                        (id, project_id, case_id, test_plan_id, test_plan_case_id, case_title,
                         bdd_given, bdd_when, bdd_then, device_serial, status, created_by,
                         timeout_sec, step_timeout_sec, queue_position, dispatched_at, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, projectId, detail.getId(), planId, planCaseId, title,
                given, when, then, serial, status, userId,
                totalSec, stepSec, position, busy ? null : now, now, now);

        if (!busy) {
            boolean dispatched = dispatchOne(id);
            if (!dispatched) {
                dispatchNext(serial);
            }
        }
        return get(id);
    }

    // ------------------------------------------------------------- dispatch

    /**
     * Marks the task DISPATCHED and sends it to the device agent.
     *
     * @return true when the agent accepted the task
     */
    private boolean dispatchOne(String id) {
        Map<String, Object> row = row(id);
        String serial = str(row.get("device_serial"));
        String agentTask = firstNonBlank(str(row.get("task_id")), IDGenerator.nextStr());
        long now = System.currentTimeMillis();
        jdbc.update("""
                        UPDATE ui_execution_task
                        SET status = 'DISPATCHED', task_id = ?, dispatched_at = ?, queue_position = 0, update_time = ?
                        WHERE id = ?
                        """,
                agentTask, now, now, id);
        try {
            deviceAgentClient.dispatch(payload(row(id)));
            log.info("ui-execution dispatched msTask={} agentTask={} device={}", id, agentTask, serial);
            return true;
        } catch (RuntimeException e) {
            log.warn("ui-execution dispatch failed msTask={}: {}", id, e.getMessage());
            finishTask(id, "FAILED", "EXECUTOR_UNAVAILABLE", "下发设备失败: " + e.getMessage());
            return false;
        }
    }

    private void dispatchNext(String serial) {
        while (true) {
            String next = firstPending(serial);
            if (next == null) {
                return;
            }
            if (dispatchOne(next)) {
                return;
            }
        }
    }

    private String firstPending(String serial) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM ui_execution_task WHERE device_serial = ? AND status = 'PENDING' "
                        + "ORDER BY create_time ASC, id ASC LIMIT 1", serial);
        return rows.isEmpty() ? null : str(rows.get(0).get("id"));
    }

    private int activeCount(String serial) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ui_execution_task WHERE device_serial = ? "
                        + "AND status IN ('PENDING','DISPATCHED','RUNNING')", Integer.class, serial);
        return count == null ? 0 : count;
    }

    private int pendingCount(String serial) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ui_execution_task WHERE device_serial = ? AND status = 'PENDING'",
                Integer.class, serial);
        return count == null ? 0 : count;
    }

    // ---------------------------------------------------------------- read

    public TaskView get(String id) {
        Map<String, Object> row = row(id);
        List<StepView> steps = new ArrayList<>();
        for (Map<String, Object> step : jdbc.queryForList(
                "SELECT * FROM ui_execution_step WHERE task_row_id = ? ORDER BY seq ASC", id)) {
            steps.add(new StepView(str(step.get("id")), num(step.get("seq")), str(step.get("status")),
                    str(step.get("decision")), str(step.get("action")), longOrNull(step.get("elapsed_ms"))));
        }
        String status = str(row.get("status"));
        Integer position = null;
        if ("PENDING".equals(status)) {
            Integer ahead = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ui_execution_task WHERE device_serial = ? AND status = 'PENDING' "
                            + "AND (create_time < ? OR (create_time = ? AND id < ?))",
                    Integer.class, str(row.get("device_serial")), row.get("create_time"),
                    row.get("create_time"), id);
            position = (ahead == null ? 0 : ahead) + 1;
        }
        String reportUrl = row.get("failure_report") == null ? null
                : "/test-plan/ui-execution/tasks/" + id + "/report";
        return new TaskView(str(row.get("id")), str(row.get("project_id")), str(row.get("case_id")),
                str(row.get("test_plan_id")), str(row.get("test_plan_case_id")), str(row.get("case_title")),
                str(row.get("bdd_given")), str(row.get("bdd_when")), str(row.get("bdd_then")),
                str(row.get("device_serial")), str(row.get("task_id")), status,
                str(row.get("summary")), str(row.get("failure_reason")), reportUrl, str(row.get("created_by")),
                num(row.get("timeout_sec")), num(row.get("step_timeout_sec")), position,
                longOrNull(row.get("dispatched_at")), longOrNull(row.get("finished_at")),
                num(row.get("create_time")), num(row.get("update_time")), steps);
    }

    /** Returns the stored failure report as a JSON object, or {@code null} for successful tasks. */
    public Object report(String id) {
        Map<String, Object> row = row(id);
        String report = str(row.get("failure_report"));
        if (report == null || report.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(report, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException("失败报告解析失败: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------- callbacks

    public void appendStep(String id, StepCallback callback) {
        requireTask(id);
        jdbc.update("""
                        INSERT INTO ui_execution_step
                        (id, task_row_id, seq, status, decision, action, elapsed_ms, create_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                IDGenerator.nextStr(), id, callback.seq(), callback.status(), callback.decision(),
                callback.action(), callback.elapsedMs(), System.currentTimeMillis());
        jdbc.update("UPDATE ui_execution_task SET status = 'RUNNING', update_time = ? "
                + "WHERE id = ? AND status = 'DISPATCHED'", System.currentTimeMillis(), id);
    }

    @Transactional(rollbackFor = Exception.class)
    public synchronized void complete(String id, ResultCallback callback) {
        Map<String, Object> row = row(id);
        if (isTerminal(str(row.get("status")))) {
            return;
        }
        String status = firstNonBlank(callback.status(), "FAILED");
        finishTask(id, status, callback.reasonCategory(), callback.summary());
        dispatchNext(str(row.get("device_serial")));
    }

    @Transactional(rollbackFor = Exception.class)
    public synchronized TaskView cancel(String id, String userId) {
        Map<String, Object> row = row(id);
        String status = str(row.get("status"));
        if (isTerminal(status)) {
            return get(id);
        }
        if (!"PENDING".equals(status) && str(row.get("task_id")) != null) {
            try {
                deviceAgentClient.cancel(str(row.get("task_id")));
            } catch (RuntimeException e) {
                log.warn("ui-execution cancel signal failed msTask={}: {}", id, e.getMessage());
            }
        }
        finishTask(id, "CANCELLED", "MANUAL_INTERRUPT", "任务被人工取消");
        dispatchNext(str(row.get("device_serial")));
        return get(id);
    }

    // ------------------------------------------------------------- watchdog

    @Scheduled(fixedDelay = 10_000)
    public void watchdog() {
        long now = System.currentTimeMillis();
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM ui_execution_task WHERE status IN ('DISPATCHED','RUNNING') "
                        + "AND dispatched_at IS NOT NULL AND dispatched_at + timeout_sec * 1000 + ? < ?",
                String.class, WATCHDOG_GRACE_MS, now);
        for (String id : ids) {
            synchronized (this) {
                Map<String, Object> row = row(id);
                if (!isTerminal(str(row.get("status")))) {
                    log.warn("ui-execution watchdog timeout msTask={} status={}", id, row.get("status"));
                    finishTask(id, "TIMEOUT", "TIMEOUT", "执行超时");
                    dispatchNext(str(row.get("device_serial")));
                }
            }
        }
    }

    // ------------------------------------------------------------- associate

    /**
     * Ensures a functional case is associated with a MeterSphere test plan and
     * returns the {@code test_plan_functional_case} row id, which is exactly what
     * {@code POST /test-plan/functional/case/run} expects as {@code id}.
     */
    @Transactional(rollbackFor = Exception.class)
    public String associate(String testPlanId, String caseId, String userId) {
        if (isBlank(testPlanId) || isBlank(caseId)) {
            throw new IllegalArgumentException("testPlanId 与 caseId 均不能为空");
        }
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id FROM test_plan_functional_case WHERE test_plan_id = ? AND functional_case_id = ? LIMIT 1",
                testPlanId, caseId);
        if (!existing.isEmpty()) {
            return str(existing.get(0).get("id"));
        }
        List<Map<String, Object>> collections = jdbc.queryForList(
                "SELECT id FROM test_plan_collection WHERE test_plan_id = ? AND type = 'FUNCTIONAL' "
                        + "AND parent_id IS NOT NULL ORDER BY create_time ASC LIMIT 1", testPlanId);
        if (collections.isEmpty()) {
            throw new IllegalStateException("测试计划没有默认功能用例测试集: " + testPlanId);
        }
        String collectionId = str(collections.get(0).get("id"));
        Long maxPos = jdbc.queryForObject(
                "SELECT COALESCE(MAX(pos), 0) FROM test_plan_functional_case WHERE test_plan_collection_id = ?",
                Long.class, collectionId);
        String id = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        jdbc.update("""
                        INSERT INTO test_plan_functional_case
                        (id, test_plan_id, functional_case_id, create_time, create_user,
                         last_exec_result, pos, test_plan_collection_id)
                        VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)
                        """,
                id, testPlanId, caseId, now, userId, (maxPos == null ? 0 : maxPos) + 8192, collectionId);
        log.info("ui-execution associated case={} to plan={} planCase={}", caseId, testPlanId, id);
        return id;
    }

    // -------------------------------------------------------------- terminal

    private void finishTask(String id, String status, String reason, String summary) {
        long now = System.currentTimeMillis();
        jdbc.update("""
                        UPDATE ui_execution_task
                        SET status = ?, failure_reason = ?, summary = ?, finished_at = ?, update_time = ?
                        WHERE id = ?
                        """,
                status, reason, summary, now, now, id);
        if (!"SUCCESS".equals(status)) {
            String report = buildReport(id, status, reason, summary);
            jdbc.update("UPDATE ui_execution_task SET failure_report = ? WHERE id = ?", report, id);
        }
        TaskView task = get(id);
        writePlanResult(task, status, reason, summary);
    }

    private String buildReport(String id, String status, String reason, String summary) {
        Map<String, Object> row = row(id);
        List<Map<String, Object>> steps = jdbc.queryForList(
                "SELECT seq, status, decision, action, elapsed_ms FROM ui_execution_step "
                        + "WHERE task_row_id = ? ORDER BY seq ASC", id);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportId", IDGenerator.nextStr());
        report.put("taskId", id);
        report.put("caseId", row.get("case_id"));
        report.put("caseTitle", row.get("case_title"));
        report.put("status", status);
        report.put("reasonCategory", reason);
        report.put("summary", summary);
        report.put("generatedAt", System.currentTimeMillis());
        report.put("environment", Map.of(
                "executorType", "AI_RUNTIME",
                "deviceSerial", String.valueOf(row.get("device_serial")),
                "testPlanId", String.valueOf(row.get("test_plan_id")),
                "testPlanCaseId", String.valueOf(row.get("test_plan_case_id"))));
        report.put("timeline", steps);
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            return "{\"taskId\":\"" + id + "\",\"summary\":\"report serialization failed\"}";
        }
    }

    private void writePlanResult(TaskView task, String status, String reason, String summary) {
        if (isBlank(task.testPlanCaseId()) || isBlank(task.testPlanId())) {
            return;
        }
        try {
            TestPlanCaseRunRequest run = new TestPlanCaseRunRequest();
            run.setProjectId(firstNonBlank(task.projectId(), ""));
            run.setId(task.testPlanCaseId());
            run.setTestPlanId(task.testPlanId());
            run.setCaseId(task.caseId());
            run.setLastExecResult(mapStatus(status));
            StringBuilder content = new StringBuilder("TestHub 设备执行结果");
            content.append("\n状态: ").append(status);
            if (reason != null) {
                content.append("\n失败分类: ").append(reason);
            }
            if (summary != null) {
                content.append("\n摘要: ").append(summary);
            }
            if (task.taskId() != null) {
                content.append("\n设备任务: ").append(task.taskId());
            }
            if (task.reportUrl() != null) {
                content.append("\n失败报告: ").append(task.reportUrl());
            }
            run.setContent(content.toString());
            testPlanFunctionalCaseService.run(run,
                    new LogInsertModule(task.createdBy(),
                            "/test-plan/ui-execution/tasks/" + task.id() + "/result", "POST"));
            log.info("ui-execution result written to plan case={} status={}",
                    task.testPlanCaseId(), run.getLastExecResult());
        } catch (RuntimeException e) {
            log.warn("ui-execution result write-back failed msTask={}: {}", task.id(), e.getMessage());
        }
    }

    // --------------------------------------------------------------- helpers

    private Map<String, Object> payload(Map<String, Object> row) {
        String id = str(row.get("id"));
        String callbackBase = trim(properties.getCallbackBaseUrl()) + "/test-plan/ui-execution/tasks/" + id;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", str(row.get("task_id")));
        payload.put("executorType", "AI_RUNTIME");
        payload.put("caseId", row.get("case_id"));
        payload.put("caseTitle", row.get("case_title"));
        payload.put("bdd", Map.of("given", str(row.get("bdd_given")),
                "when", str(row.get("bdd_when")), "then", str(row.get("bdd_then"))));
        payload.put("script", null);
        payload.put("device", Map.of("serial", str(row.get("device_serial")),
                "platform", "android", "tags", List.of("android")));
        payload.put("skills", null);
        payload.put("timeouts", Map.of("totalSec", num(row.get("timeout_sec")),
                "stepSec", num(row.get("step_timeout_sec"))));
        payload.put("model", Map.of("provider", properties.getModelProvider(),
                "endpoint", properties.getModelEndpoint(),
                "apiKey", properties.getModelApiKey(),
                "modelId", properties.getModelId()));
        payload.put("callbacks", Map.of(
                "stepUrl", callbackBase + "/steps",
                "logUrl", callbackBase + "/logs",
                "resultUrl", callbackBase + "/result",
                "token", properties.getAgentToken()));
        return payload;
    }

    private Map<String, Object> row(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ui_execution_task WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        return rows.get(0);
    }

    private void requireTask(String id) {
        row(id);
    }

    private static boolean isTerminal(String status) {
        return status != null && switch (status) {
            case "SUCCESS", "FAILED", "TIMEOUT", "CANCELLED", "BLOCKED" -> true;
            default -> false;
        };
    }

    private static String mapStatus(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "SUCCESS" -> "SUCCESS";
            case "FAILED" -> "ERROR";
            case "TIMEOUT", "CANCELLED" -> "BLOCKED";
            default -> "PENDING";
        };
    }

    private static String text(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() > 4000 ? result.substring(0, 4000) : result;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int num(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static String trim(String value) {
        String base = value == null || value.isBlank() ? "http://127.0.0.1:8082" : value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
