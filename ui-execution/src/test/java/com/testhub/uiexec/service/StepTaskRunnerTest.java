package com.testhub.uiexec.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.uiexec.client.AgentClient;
import com.testhub.uiexec.client.AgentUnavailableException;
import com.testhub.uiexec.domain.FailureCategory;
import com.testhub.uiexec.domain.StepSpec;
import com.testhub.uiexec.domain.TaskStatus;
import com.testhub.uiexec.web.dto.TaskDtos.StepCallbackRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StepTaskRunnerTest {

    @Mock
    private AgentClient agentClient;

    @Mock
    private StepService stepService;

    private StepTaskRunner runner;

    @BeforeEach
    void setUp() {
        runner = new StepTaskRunner(agentClient, stepService, new ObjectMapper());
    }

    @Test
    void runsEveryEnabledStepAndRecordsEvidence() {
        when(agentClient.action(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("ok", true, "message", "done"));
        when(agentClient.uiTree(anyString(), anyString())).thenReturn(uiTree("首页"));
        when(agentClient.screenshot(anyString(), anyString())).thenReturn(new byte[]{1, 2, 3});

        StepTaskRunner.StepRunResult result = runner.run(context(List.of(
                step(1, "CLICK", "TEXT", "登录", null, null, null, true),
                step(2, "INPUT", "ID", "username", "tester", null, null, true),
                step(3, "ASSERT_TEXT", "TEXT", "首页", null, null, 5000, true),
                step(4, "SCREENSHOT", null, null, null, null, null, true))));

        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(result.summary()).isEqualTo("4/4 steps passed");
        assertThat(result.failureReason()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<StepCallbackRequest> captor = ArgumentCaptor.forClass(StepCallbackRequest.class);
        verify(stepService, times(4)).upsert(eq("task-step-1"), captor.capture());
        List<StepCallbackRequest> recorded = captor.getAllValues();
        assertThat(recorded).extracting(StepCallbackRequest::seq).containsExactly(1, 2, 3, 4);
        assertThat(recorded).allMatch(step -> "OK".equals(step.status()));
        assertThat(recorded.get(0).screenshotBase64()).isEqualTo("AQID");
        assertThat(recorded.get(2).decision()).contains("ASSERT_TEXT");
        verify(agentClient, times(1)).uiTree("http://agent", "serial-1");
    }

    @Test
    void assertionFailureFinishesWithAssertionFailed() {
        when(agentClient.uiTree(anyString(), anyString())).thenReturn(uiTree("登录失败"));
        when(agentClient.screenshot(anyString(), anyString())).thenReturn(new byte[]{1});

        StepTaskRunner.StepRunResult result = runner.run(context(List.of(
                step(1, "ASSERT_TEXT", "TEXT", "首页", null, "登录成功", null, true))));

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo(FailureCategory.ASSERTION_FAILED);
        assertThat(result.summary()).contains("ASSERT_TEXT", "0/1");

        ArgumentCaptor<StepCallbackRequest> captor = ArgumentCaptor.forClass(StepCallbackRequest.class);
        verify(stepService).upsert(eq("task-step-1"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("FAIL");
        assertThat(captor.getValue().decision()).contains("登录成功");
    }

    @Test
    void actionFailureFinishesWithActionFailed() {
        when(agentClient.action(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("ok", false, "message", "element not found: 登录"));
        when(agentClient.screenshot(anyString(), anyString())).thenReturn(new byte[]{1});

        StepTaskRunner.StepRunResult result = runner.run(context(List.of(
                step(1, "CLICK", "TEXT", "登录", null, null, null, true))));

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo(FailureCategory.ACTION_FAILED);
        assertThat(result.summary()).contains("element not found");

        ArgumentCaptor<StepCallbackRequest> captor = ArgumentCaptor.forClass(StepCallbackRequest.class);
        verify(stepService).upsert(eq("task-step-1"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("FAIL");
        assertThat(captor.getValue().action()).contains("element not found");
    }

    @Test
    void stepTimeoutIsEnforced() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(agentClient.action(anyString(), anyString(), anyMap())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return Map.of("ok", true);
        });

        Instant startedAt = Instant.now();
        StepTaskRunner.StepRunResult result = runner.run(context(List.of(
                step(1, "CLICK", "TEXT", "登录", null, null, 100, true))));

        release.countDown();

        assertThat(result.status()).isEqualTo(TaskStatus.TIMEOUT);
        assertThat(result.failureReason()).isEqualTo(FailureCategory.TIMEOUT);
        assertThat(result.summary()).contains("超时");
        assertThat(Instant.now()).isAfterOrEqualTo(startedAt);

        ArgumentCaptor<StepCallbackRequest> captor = ArgumentCaptor.forClass(StepCallbackRequest.class);
        verify(stepService).upsert(eq("task-step-1"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("FAIL");
        assertThat(captor.getValue().decision()).contains("timed out");
    }

    @Test
    void screenshotFailureIsBestEffort() {
        when(agentClient.action(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("ok", true, "message", "clicked"));
        when(agentClient.screenshot(anyString(), anyString()))
                .thenThrow(new AgentUnavailableException("screenshot down"));

        StepTaskRunner.StepRunResult result = runner.run(context(List.of(
                step(1, "CLICK", "TEXT", "登录", null, null, null, true))));

        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(result.summary()).isEqualTo("1/1 steps passed");
    }

    @Test
    void disabledStepsAreSkippedAndSequencesArePreserved() {
        when(agentClient.action(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("ok", true, "message", "clicked"));
        when(agentClient.screenshot(anyString(), anyString())).thenReturn(new byte[]{1});

        StepTaskRunner.StepRunResult result = runner.run(context(List.of(
                step(5, "CLICK", "TEXT", "登录", null, null, null, true),
                step(9, "CLICK", "TEXT", "忽略", null, null, null, false))));

        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        ArgumentCaptor<StepCallbackRequest> captor = ArgumentCaptor.forClass(StepCallbackRequest.class);
        verify(stepService).upsert(eq("task-step-1"), captor.capture());
        assertThat(captor.getValue().seq()).isEqualTo(5);
        verify(agentClient, times(1)).action(anyString(), anyString(), anyMap());
    }

    private StepTaskRunner.Context context(List<StepSpec> steps) {
        return new StepTaskRunner.Context("task-step-1", "http://agent", "serial-1", "步骤用例",
                steps, 60, 10, Instant.now(), () -> false);
    }

    private StepSpec step(Integer seq, String stepType, String selectorType, String selector, String value,
                          String assertValue, Integer timeoutMs, Boolean enabled) {
        return new StepSpec(seq, stepType, selectorType, selector, value, assertValue, timeoutMs, enabled);
    }

    private Map<String, Object> uiTree(String... texts) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("id", "home");
        element.put("text", texts.length == 0 ? "" : texts[0]);
        element.put("type", "text");
        element.put("clickable", false);
        element.put("editable", false);
        element.put("bounds", Map.of("x", 0, "y", 0, "width", 1080, "height", 100));

        Map<String, Object> screen = new LinkedHashMap<>();
        screen.put("app", "demo");
        screen.put("title", "demo");
        screen.put("texts", List.of(texts));
        screen.put("elements", List.of(element));

        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("width", 1080);
        tree.put("height", 1920);
        tree.put("screen", screen);
        tree.put("elements", List.of(element));
        return tree;
    }
}
