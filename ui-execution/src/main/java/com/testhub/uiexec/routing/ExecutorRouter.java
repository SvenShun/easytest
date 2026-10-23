package com.testhub.uiexec.routing;

import com.testhub.uiexec.domain.ExecutorType;
import org.springframework.stereotype.Component;

/**
 * Pure executor routing rules (docs/V1-SCOPE.md §4):
 *
 * <ol>
 *   <li>case not APPROVED → {@code CASE_NOT_APPROVED}</li>
 *   <li>requested SCRIPT, available → SCRIPT; no script → {@code EXECUTOR_UNAVAILABLE}</li>
 *   <li>requested AI_RUNTIME, vision configured → AI_RUNTIME</li>
 *   <li>requested AI_RUNTIME, no vision, script available → fall back to SCRIPT</li>
 *   <li>requested AI_RUNTIME, no vision, no script → immediate FAILED ({@code EXECUTOR_UNAVAILABLE})</li>
 *   <li>not requested: script → SCRIPT; no script → AI_RUNTIME (requires vision)</li>
 * </ol>
 */
@Component
public class ExecutorRouter {

    public static final String CASE_NOT_APPROVED = "CASE_NOT_APPROVED";
    public static final String EXECUTOR_UNAVAILABLE = "EXECUTOR_UNAVAILABLE";
    private static final String NO_SCRIPT_MESSAGE = "用例未关联脚本，无法使用 SCRIPT 执行器";
    private static final String NO_VISION_AND_SCRIPT_MESSAGE = "视觉模型未配置且用例无脚本，无法执行";

    public RoutingDecision route(RoutingInput input) {
        if (!input.approved()) {
            return RoutingDecision.reject(CASE_NOT_APPROVED, "用例未通过评审，不能创建执行任务");
        }
        ExecutorType requested = input.requested();
        if (requested == ExecutorType.SCRIPT) {
            return input.hasScript()
                    ? RoutingDecision.ok(ExecutorType.SCRIPT)
                    : RoutingDecision.reject(EXECUTOR_UNAVAILABLE, NO_SCRIPT_MESSAGE);
        }
        if (requested == ExecutorType.AI_RUNTIME) {
            return aiRuntimeDecision(input.hasScript(), input.visionConfigured());
        }
        // Not requested: scripts are the deterministic default, AI Runtime otherwise.
        if (input.hasScript()) {
            return RoutingDecision.ok(ExecutorType.SCRIPT);
        }
        return aiRuntimeDecision(false, input.visionConfigured());
    }

    private RoutingDecision aiRuntimeDecision(boolean hasScript, boolean visionConfigured) {
        if (visionConfigured) {
            return RoutingDecision.ok(ExecutorType.AI_RUNTIME);
        }
        if (hasScript) {
            return RoutingDecision.fallback(ExecutorType.SCRIPT, "视觉模型未配置，已回退 SCRIPT 执行器");
        }
        return RoutingDecision.immediateFailure(ExecutorType.AI_RUNTIME, EXECUTOR_UNAVAILABLE,
                NO_VISION_AND_SCRIPT_MESSAGE);
    }
}
