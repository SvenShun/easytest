package com.testhub.uiexec.routing;

import com.testhub.uiexec.domain.ExecutorType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorRouterTest {

    private final ExecutorRouter router = new ExecutorRouter();

    @Test
    void explicitScriptWithScriptUsesScript() {
        RoutingDecision decision = router.route(new RoutingInput(true, true, ExecutorType.SCRIPT, false));

        assertThat(decision.ok()).isTrue();
        assertThat(decision.executorType()).isEqualTo(ExecutorType.SCRIPT);
        assertThat(decision.hasFallback()).isFalse();
    }

    @Test
    void explicitAiRuntimeWithVisionUsesAiRuntime() {
        RoutingDecision decision = router.route(new RoutingInput(true, false, ExecutorType.AI_RUNTIME, true));

        assertThat(decision.ok()).isTrue();
        assertThat(decision.executorType()).isEqualTo(ExecutorType.AI_RUNTIME);
    }

    @Test
    void unspecifiedWithScriptUsesScript() {
        RoutingDecision decision = router.route(new RoutingInput(true, true, null, true));

        assertThat(decision.executorType()).isEqualTo(ExecutorType.SCRIPT);
        assertThat(decision.hasFallback()).isFalse();
    }

    @Test
    void unspecifiedWithoutScriptUsesAiRuntimeWhenVisionConfigured() {
        RoutingDecision decision = router.route(new RoutingInput(true, false, null, true));

        assertThat(decision.executorType()).isEqualTo(ExecutorType.AI_RUNTIME);
    }

    @Test
    void unapprovedCaseIsRejectedWithCaseNotApproved() {
        RoutingDecision decision = router.route(new RoutingInput(false, true, null, true));

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.errorCode()).isEqualTo("CASE_NOT_APPROVED");
    }

    @Test
    void explicitScriptWithoutScriptIsRejectedAsExecutorUnavailable() {
        RoutingDecision decision = router.route(new RoutingInput(true, false, ExecutorType.SCRIPT, true));

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.errorCode()).isEqualTo("EXECUTOR_UNAVAILABLE");
    }

    @Test
    void aiRuntimeWithoutVisionFallsBackToScriptWhenScriptExists() {
        RoutingDecision decision = router.route(new RoutingInput(true, true, ExecutorType.AI_RUNTIME, false));

        assertThat(decision.ok()).isTrue();
        assertThat(decision.executorType()).isEqualTo(ExecutorType.SCRIPT);
        assertThat(decision.hasFallback()).isTrue();
        assertThat(decision.note()).contains("视觉模型未配置");
    }

    @Test
    void aiRuntimeWithoutVisionAndWithoutScriptIsImmediateFailure() {
        RoutingDecision decision = router.route(new RoutingInput(true, false, ExecutorType.AI_RUNTIME, false));

        assertThat(decision.immediateFailure()).isTrue();
        assertThat(decision.rejected()).isFalse();
        assertThat(decision.executorType()).isEqualTo(ExecutorType.AI_RUNTIME);
        assertThat(decision.errorCode()).isEqualTo("EXECUTOR_UNAVAILABLE");
    }

    @Test
    void reviewStatusHelperMapsApprovedCaseInsensitively() {
        RoutingInput input = RoutingInput.ofReviewStatus("approved", true, null, false);

        assertThat(input.approved()).isTrue();
    }
}
