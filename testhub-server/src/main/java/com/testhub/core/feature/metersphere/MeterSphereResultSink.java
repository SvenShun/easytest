package com.testhub.core.feature.metersphere;

import com.testhub.core.feature.plan.PlanCase;
import com.testhub.core.feature.plan.PlanRun;
import com.testhub.core.feature.plan.PlanRunItem;
import com.testhub.core.feature.plan.RunItemStatus;
import com.testhub.core.feature.plan.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a TestHub plan-run result into a MeterSphere test plan through
 * {@code POST /test-plan/functional/case/run}
 * (source: {@code TestPlanFunctionalCaseController#run}, request
 * {@code TestPlanCaseRunRequest}).
 *
 * <p>Mapping: {@code id} is the MeterSphere plan-case row id, {@code caseId} the
 * functional case id, {@code lastExecResult} one of PENDING/SUCCESS/BLOCKED/ERROR.
 * The call is best-effort from the plan callback path; failures are logged and
 * never roll back the TestHub result.
 */
@Component
public class MeterSphereResultSink {

    private static final Logger log = LoggerFactory.getLogger(MeterSphereResultSink.class);

    private final MeterSphereProperties properties;
    private final MeterSphereSession session;
    private final RestClient client;

    public MeterSphereResultSink(MeterSphereProperties properties, MeterSphereSession session) {
        this.properties = properties;
        this.session = session;
        this.client = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .build();
    }

    public boolean applicable(TestPlan plan, PlanCase planCase) {
        return properties.isEnabled()
                && hasText(properties.getProjectId())
                && session.available()
                && plan != null && hasText(plan.getMetersphereTestPlanId())
                && planCase != null && hasText(planCase.getMetersphereCaseId())
                && hasText(planCase.getMetersphereTestPlanCaseId());
    }

    public void write(TestPlan plan, PlanCase planCase, PlanRun run, PlanRunItem item) {
        if (!applicable(plan, planCase)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", properties.getProjectId());
        body.put("id", planCase.getMetersphereTestPlanCaseId());
        body.put("testPlanId", plan.getMetersphereTestPlanId());
        body.put("caseId", planCase.getMetersphereCaseId());
        body.put("lastExecResult", mapStatus(item.getStatus()));
        body.put("content", content(run, item));
        try {
            post(body, plan, planCase, item);
        } catch (RuntimeException first) {
            // Session expiry (or a transient auth failure): log in again and retry once.
            log.info("MeterSphere 回写失败，刷新会话后重试: {}", first.getMessage());
            session.refresh();
            try {
                post(body, plan, planCase, item);
            } catch (RuntimeException second) {
                log.warn("MeterSphere 结果回写失败 plan={} case={}: {}",
                        plan.getId(), planCase.getCaseId(), second.getMessage());
            }
        }
    }

    private void post(Map<String, Object> body, TestPlan plan, PlanCase planCase, PlanRunItem item) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = client.post()
                .uri("/test-plan/functional/case/run")
                .headers(headers -> headers.setAll(session.headers()))
                .body(body)
                .retrieve()
                .body(Map.class);
        Object code = response == null ? null : response.get("code");
        if (code != null && !"100200".equals(String.valueOf(code))) {
            throw new IllegalStateException("MeterSphere 返回异常: " + response);
        }
        log.info("MeterSphere 结果已回写 plan={} case={} planCase={} status={}",
                plan.getId(), planCase.getCaseId(), planCase.getMetersphereTestPlanCaseId(),
                item.getStatus());
    }

    /** TestHub status → MeterSphere FunctionalCaseExecuteStatus. */
    static String mapStatus(RunItemStatus status) {
        if (status == null) {
            return "PENDING";
        }
        return switch (status) {
            case SUCCESS -> "SUCCESS";
            case FAILED -> "ERROR";
            case TIMEOUT, CANCELLED, SKIPPED -> "BLOCKED";
            default -> "PENDING";
        };
    }

    private String content(PlanRun run, PlanRunItem item) {
        StringBuilder content = new StringBuilder("TestHub 计划执行结果");
        content.append("\n计划运行: ").append(run.getId());
        content.append("\n用例: ").append(item.getCaseTitle() == null ? item.getCaseId() : item.getCaseTitle());
        content.append("\n状态: ").append(item.getStatus());
        if (item.getFailureReason() != null) {
            content.append("\n失败分类: ").append(item.getFailureReason());
        }
        if (item.getSummary() != null) {
            content.append("\n摘要: ").append(item.getSummary());
        }
        if (item.getFailureReportUrl() != null) {
            content.append("\n失败轨迹报告: ").append(item.getFailureReportUrl());
        }
        if (item.getTaskId() != null) {
            content.append("\n任务: ").append(item.getTaskId());
        }
        return content.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        String base = value == null || value.isBlank() ? "http://127.0.0.1:8082" : value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
