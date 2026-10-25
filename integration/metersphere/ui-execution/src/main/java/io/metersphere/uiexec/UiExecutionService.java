package io.metersphere.uiexec;

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
 * stored in MeterSphere's database with a Flyway-managed table.</p>
 */
@Service
public class UiExecutionService {

    private static final Logger log = LoggerFactory.getLogger(UiExecutionService.class);

    private final JdbcTemplate jdbc;
    private final FunctionalCaseService functionalCaseService;
    private final TestPlanFunctionalCaseService testPlanFunctionalCaseService;
    private final DeviceAgentClient deviceAgentClient;
    private final UiExecutionProperties properties;

    public UiExecutionService(JdbcTemplate jdbc, FunctionalCaseService functionalCaseService,
                              TestPlanFunctionalCaseService testPlanFunctionalCaseService,
                              DeviceAgentClient deviceAgentClient, UiExecutionProperties properties) {
        this.jdbc = jdbc;
        this.functionalCaseService = functionalCaseService;
        this.testPlanFunctionalCaseService = testPlanFunctionalCaseService;
        this.deviceAgentClient = deviceAgentClient;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskView create(CreateTaskRequest request, String userId, String projectIdFromSession) {
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
        long now = System.currentTimeMillis();
        jdbc.update("""
                        INSERT INTO ui_execution_task
                        (id, project_id, case_id, test_plan_id, test_plan_case_id, case_title,
                         bdd_given, bdd_when, bdd_then, device_serial, status, created_by, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, projectId, detail.getId(), planId, planCaseId, title,
                given, when, then, serial, "PENDING", userId, now, now);

        String taskId = IDGenerator.nextStr();
        jdbc.update("UPDATE ui_execution_task SET task_id = ?, status = ?, update_time = ? WHERE id = ?",
                taskId, "DISPATCHED", System.currentTimeMillis(), id);
        try {
            deviceAgentClient.dispatch(payload(id, taskId, detail.getId(), title, given, when, then,
                    serial, totalSec, stepSec));
            log.info("ui-execution task dispatched msTask={} agentTask={} case={}", id, taskId, detail.getId());
        } catch (RuntimeException e) {
            log.warn("ui-execution dispatch failed msTask={}: {}", id, e.getMessage());
            jdbc.update("UPDATE ui_execution_task SET status = ?, summary = ?, update_time = ? WHERE id = ?",
                    "FAILED", "下发设备失败: " + e.getMessage(), System.currentTimeMillis(), id);
        }
        return get(id);
    }

    public TaskView get(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ui_execution_task WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        Map<String, Object> row = rows.get(0);
        List<StepView> steps = new ArrayList<>();
        for (Map<String, Object> step : jdbc.queryForList(
                "SELECT * FROM ui_execution_step WHERE task_row_id = ? ORDER BY seq ASC", id)) {
            steps.add(new StepView(str(step.get("id")), num(step.get("seq")), str(step.get("status")),
                    str(step.get("decision")), str(step.get("action")), longOrNull(step.get("elapsed_ms"))));
        }
        return new TaskView(str(row.get("id")), str(row.get("project_id")), str(row.get("case_id")),
                str(row.get("test_plan_id")),
                str(row.get("test_plan_case_id")), str(row.get("case_title")),
                str(row.get("bdd_given")), str(row.get("bdd_when")), str(row.get("bdd_then")),
                str(row.get("device_serial")), str(row.get("task_id")), str(row.get("status")),
                str(row.get("summary")), str(row.get("failure_reason")), str(row.get("report_url")),
                str(row.get("created_by")), num(row.get("create_time")), num(row.get("update_time")), steps);
    }

    public void appendStep(String id, StepCallback callback) {
        requireTask(id);
        jdbc.update("""
                        INSERT INTO ui_execution_step
                        (id, task_row_id, seq, status, decision, action, elapsed_ms, create_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                IDGenerator.nextStr(), id, callback.seq(), callback.status(), callback.decision(),
                callback.action(), callback.elapsedMs(), System.currentTimeMillis());
        jdbc.update("UPDATE ui_execution_task SET status = ?, update_time = ? WHERE id = ? AND status = ?",
                "RUNNING", System.currentTimeMillis(), id, "DISPATCHED");
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(String id, ResultCallback callback) {
        TaskView task = get(id);
        String status = firstNonBlank(callback.status(), "FAILED");
        String summary = callback.summary();
        String reportUrl = null;
        jdbc.update("""
                        UPDATE ui_execution_task
                        SET status = ?, failure_reason = ?, summary = ?, report_url = ?, update_time = ?
                        WHERE id = ?
                        """,
                status, callback.reasonCategory(), summary, reportUrl, System.currentTimeMillis(), id);
        writePlanResult(task, status, callback);
    }

    private void writePlanResult(TaskView task, String status, ResultCallback callback) {
        if (isBlank(task.testPlanCaseId()) && isBlank(task.testPlanId())) {
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
            if (callback.reasonCategory() != null) {
                content.append("\n失败分类: ").append(callback.reasonCategory());
            }
            if (callback.summary() != null) {
                content.append("\n摘要: ").append(callback.summary());
            }
            if (task.taskId() != null) {
                content.append("\n设备任务: ").append(task.taskId());
            }
            run.setContent(content.toString());
            testPlanFunctionalCaseService.run(run,
                    new LogInsertModule(task.createdBy(), "/test-plan/ui-execution/tasks/" + task.id() + "/result", "POST"));
            log.info("ui-execution result written to MeterSphere plan case={} status={}",
                    task.testPlanCaseId(), run.getLastExecResult());
        } catch (RuntimeException e) {
            log.warn("ui-execution result write-back failed msTask={}: {}", task.id(), e.getMessage());
        }
    }

    private Map<String, Object> payload(String id, String taskId, String caseId, String title,
                                        String given, String when, String then, String serial,
                                        int totalSec, int stepSec) {
        String callbackBase = trim(properties.getCallbackBaseUrl()) + "/test-plan/ui-execution/tasks/" + id;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("executorType", "AI_RUNTIME");
        payload.put("caseId", caseId);
        payload.put("caseTitle", title);
        payload.put("bdd", Map.of("given", given, "when", when, "then", then));
        payload.put("script", null);
        payload.put("device", Map.of("serial", serial, "platform", "android", "tags", List.of("android")));
        payload.put("skills", null);
        payload.put("timeouts", Map.of("totalSec", totalSec, "stepSec", stepSec));
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

    private void requireTask(String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ui_execution_task WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
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
