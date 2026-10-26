package com.testhub.uiexec.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.uiexec.client.AgentClient;
import com.testhub.uiexec.client.dto.DeviceUiTree;
import com.testhub.uiexec.domain.FailureCategory;
import com.testhub.uiexec.domain.StepSpec;
import com.testhub.uiexec.domain.TaskStatus;
import com.testhub.uiexec.web.dto.TaskDtos.StepCallbackRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Deterministic executor for {@code STEP} tasks.
 *
 * <p>Every enabled {@link StepSpec} is translated into one device-agent action (or a local wait /
 * UI-tree assertion), recorded through {@link StepService#upsert} and optionally illustrated with a
 * best-effort screenshot. Per-step {@code timeoutMs} and the task-wide {@code timeoutSec} are both
 * enforced. The runner is deliberately free of {@link TaskService} to avoid a dependency cycle:
 * callers pass a {@link Context} and apply the returned {@link StepRunResult} through the normal
 * task-finish path.</p>
 */
@Component
public class StepTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(StepTaskRunner.class);
    private static final int DEFAULT_TIMEOUT_SEC = 120;
    private static final int DEFAULT_STEP_TIMEOUT_SEC = 30;
    private static final int MAX_SCREENSHOT_BYTES = 2 * 1024 * 1024;
    private static final long MAX_SCREENSHOT_TIMEOUT_MS = 5_000L;

    private final AgentClient agentClient;
    private final StepService stepService;
    private final ObjectMapper objectMapper;

    public StepTaskRunner(AgentClient agentClient, StepService stepService, ObjectMapper objectMapper) {
        this.agentClient = agentClient;
        this.stepService = stepService;
        this.objectMapper = objectMapper;
    }

    public StepRunResult run(Context context) {
        if (context == null || context.taskId() == null || context.taskId().isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (context.agentBaseUrl() == null || context.agentBaseUrl().isBlank()) {
            return StepRunResult.failed(FailureCategory.EXECUTOR_UNAVAILABLE, "设备 Agent 地址为空");
        }

        List<IndexedStep> steps = enabledSteps(context.steps());
        if (steps.isEmpty()) {
            return StepRunResult.failed(FailureCategory.ACTION_FAILED, "STEP 任务没有可执行的步骤");
        }
        int total = steps.size();
        int passed = 0;
        int timeoutSec = context.timeoutSec() <= 0 ? DEFAULT_TIMEOUT_SEC : context.timeoutSec();
        int stepTimeoutSec = context.stepTimeoutSec() <= 0 ? DEFAULT_STEP_TIMEOUT_SEC : context.stepTimeoutSec();
        Instant startedAt = context.startedAt() == null ? Instant.now() : context.startedAt();
        Instant deadline = startedAt.plusSeconds(timeoutSec);

        for (int index = 0; index < total; index++) {
            IndexedStep indexed = steps.get(index);
            StepSpec step = indexed.step();
            int seq = indexed.seq();
            String label = seq + " " + (step.stepTypeName() == null ? "STEP" : step.stepTypeName());

            if (isCancelled(context)) {
                return StepRunResult.cancelled("任务已取消，执行到第 " + seq + " 步");
            }
            long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
            if (remainingMs <= 0) {
                return StepRunResult.timeout("任务执行超时（" + timeoutSec + " 秒），已通过 "
                        + passed + "/" + total + " 步，停在第 " + seq + " 步");
            }
            long stepTimeoutMs = Math.min(step.timeoutMsOr(stepTimeoutSec * 1000L), remainingMs);
            long stepStart = System.nanoTime();
            StepOutcome outcome;
            try {
                outcome = executeStep(context, step, stepTimeoutMs);
            } catch (StepTimeoutException e) {
                long elapsed = elapsedMs(stepStart);
                recordStep(context, seq, "FAIL", label + " timed out", truncate(e.getMessage(), 2000), elapsed, null);
                return StepRunResult.timeout("第 " + seq + " 步 " + label + " 超时（" + stepTimeoutMs
                        + "ms），已通过 " + passed + "/" + total + " 步");
            } catch (StepCancelledException e) {
                return StepRunResult.cancelled("任务已取消，执行到第 " + seq + " 步");
            } catch (RuntimeException e) {
                long elapsed = elapsedMs(stepStart);
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                recordStep(context, seq, "FAIL", label + " failed", truncate(message, 2000), elapsed, null);
                return StepRunResult.failed(FailureCategory.ACTION_FAILED,
                        "第 " + seq + " 步 " + label + " 执行失败: " + message
                                + "（已通过 " + passed + "/" + total + " 步）");
            }
            long elapsed = elapsedMs(stepStart);
            String screenshot = captureScreenshot(context, stepTimeoutMs);
            if (outcome.ok()) {
                recordStep(context, seq, "OK", outcome.decision(), outcome.action(), elapsed, screenshot);
                passed++;
                continue;
            }
            recordStep(context, seq, "FAIL", outcome.decision(), outcome.action(), elapsed, screenshot);
            FailureCategory category = outcome.category() == null
                    ? FailureCategory.ACTION_FAILED : outcome.category();
            return StepRunResult.failed(category, "第 " + seq + " 步 " + label + " 失败: "
                    + outcome.decision() + "（已通过 " + passed + "/" + total + " 步）");
        }
        return StepRunResult.success(passed + "/" + total + " steps passed");
    }

    // ------------------------------------------------------------- steps

    private StepOutcome executeStep(Context context, StepSpec step, long timeoutMs) {
        String type = step.stepTypeName();
        if (type == null) {
            throw new StepActionException("步骤类型为空");
        }
        return switch (type) {
            case "CLICK" -> click(context, step, timeoutMs);
            case "INPUT" -> input(context, step, timeoutMs);
            case "TAP" -> tap(context, step, timeoutMs);
            case "SWIPE" -> swipe(context, step, timeoutMs);
            case "BACK" -> act(context, actionBody("back"), timeoutMs, "BACK");
            case "HOME" -> act(context, actionBody("home"), timeoutMs, "HOME");
            case "WAIT" -> waitStep(context, step, timeoutMs);
            case "LAUNCH" -> launch(context, step, timeoutMs);
            case "ASSERT_TEXT" -> assertText(context, step, timeoutMs);
            case "ASSERT_EXISTS" -> assertExists(context, step, timeoutMs);
            case "SCREENSHOT" -> StepOutcome.ok("SCREENSHOT", "captured");
            default -> throw new StepActionException("不支持的步骤类型: " + type);
        };
    }

    private StepOutcome click(Context context, StepSpec step, long timeoutMs) {
        String selector = firstNonBlank(step.selector(), step.value());
        if (selector == null) {
            throw new StepActionException("CLICK 需要 selector");
        }
        String selectorType = defaultSelectorType(step.selectorTypeName(), "TEXT");
        if ("COORD".equals(selectorType)) {
            int[] point = parsePoints(selector, 2, "CLICK");
            return tapAt(context, point[0], point[1], timeoutMs, "CLICK " + selector);
        }
        Map<String, Object> body = actionBody("click");
        body.put("target", target(selectorType, selector));
        return act(context, body, timeoutMs, "CLICK " + selectorType + "=" + selector);
    }

    private StepOutcome input(Context context, StepSpec step, long timeoutMs) {
        String selector = firstNonBlank(step.selector());
        if (selector == null) {
            throw new StepActionException("INPUT 需要 selector");
        }
        String selectorType = defaultSelectorType(step.selectorTypeName(), "TEXT");
        if ("COORD".equals(selectorType)) {
            throw new StepActionException("INPUT 不支持 COORD selector");
        }
        Map<String, Object> body = actionBody("input");
        body.put("target", target(selectorType, selector));
        body.put("text", step.value() == null ? "" : step.value());
        return act(context, body, timeoutMs, "INPUT " + selectorType + "=" + selector);
    }

    private StepOutcome tap(Context context, StepSpec step, long timeoutMs) {
        String raw = firstNonBlank(step.selector(), step.value(), step.assertValue());
        int[] point = parsePoints(raw, 2, "TAP");
        return tapAt(context, point[0], point[1], timeoutMs, "TAP " + raw);
    }

    private StepOutcome tapAt(Context context, int x, int y, long timeoutMs, String decision) {
        Map<String, Object> body = actionBody("tap");
        body.put("from", point(x, y));
        return act(context, body, timeoutMs, decision);
    }

    private StepOutcome swipe(Context context, StepSpec step, long timeoutMs) {
        String raw = firstNonBlank(step.value(), step.assertValue(), step.selector());
        int[] points = parsePoints(raw, 4, "SWIPE");
        Map<String, Object> body = actionBody("swipe");
        body.put("from", point(points[0], points[1]));
        body.put("to", point(points[2], points[3]));
        body.put("durationMs", 300);
        return act(context, body, timeoutMs, "SWIPE " + raw);
    }

    private StepOutcome launch(Context context, StepSpec step, long timeoutMs) {
        String app = firstNonBlank(step.value(), step.assertValue(), step.selector());
        if (app == null) {
            throw new StepActionException("LAUNCH 需要应用包名");
        }
        Map<String, Object> body = actionBody("launch");
        body.put("app", app);
        return act(context, body, timeoutMs, "LAUNCH " + app);
    }

    private StepOutcome waitStep(Context context, StepSpec step, long timeoutMs) {
        String raw = firstNonBlank(step.value(), step.selector(), step.assertValue());
        if (raw == null) {
            throw new StepActionException("WAIT 需要毫秒数");
        }
        long requested = parseLong(raw, "WAIT");
        long sleepMs = Math.min(requested, Math.max(0, timeoutMs));
        sleep(context, sleepMs);
        if (requested > sleepMs) {
            throw new StepTimeoutException("WAIT " + requested + "ms 超过步骤超时 " + timeoutMs + "ms");
        }
        return StepOutcome.ok("WAIT " + requested + "ms", "waited " + sleepMs + "ms");
    }

    private StepOutcome assertText(Context context, StepSpec step, long timeoutMs) {
        String expected = firstNonBlank(step.assertValue(), step.value(), step.selector());
        if (expected == null) {
            throw new StepActionException("ASSERT_TEXT 需要期望文本");
        }
        DeviceUiTree tree = uiTree(context, timeoutMs);
        boolean found = tree.allTexts().stream()
                .anyMatch(text -> text != null && text.contains(expected));
        if (!found) {
            return StepOutcome.assertionFailed("ASSERT_TEXT 未找到文本: " + expected,
                    "UI 树文本不包含期望值");
        }
        return StepOutcome.ok("ASSERT_TEXT 命中文本: " + expected, "matched");
    }

    private StepOutcome assertExists(Context context, StepSpec step, long timeoutMs) {
        String selector = firstNonBlank(step.selector(), step.value(), step.assertValue());
        if (selector == null) {
            throw new StepActionException("ASSERT_EXISTS 需要 selector");
        }
        String selectorType = defaultSelectorType(step.selectorTypeName(), "TEXT");
        DeviceUiTree tree = uiTree(context, timeoutMs);
        boolean found = switch (selectorType) {
            case "COORD" -> {
                int[] point = parsePoints(selector, 2, "ASSERT_EXISTS");
                yield tree.allElements().stream()
                        .anyMatch(element -> element != null && element.bounds() != null
                                && element.bounds().contains(point[0], point[1]));
            }
            case "ID" -> tree.allElements().stream()
                    .anyMatch(element -> element != null && equalsOrContains(element.id(), selector));
            default -> tree.allTexts().stream().anyMatch(text -> equalsOrContains(text, selector));
        };
        if (!found) {
            return StepOutcome.assertionFailed("ASSERT_EXISTS 未找到: " + selectorType + "=" + selector,
                    "UI 树中没有匹配元素");
        }
        return StepOutcome.ok("ASSERT_EXISTS 命中: " + selectorType + "=" + selector, "matched");
    }

    // --------------------------------------------------------- agent calls

    private StepOutcome act(Context context, Map<String, Object> body, long timeoutMs, String decision) {
        Object response = withTimeout(
                () -> agentClient.action(context.agentBaseUrl(), context.deviceSerial(), body), timeoutMs);
        String message = responseMessage(response, decision + " ok");
        boolean ok = responseOk(response);
        if (!ok) {
            return StepOutcome.actionFailed(decision, message);
        }
        return StepOutcome.ok(decision, message);
    }

    private DeviceUiTree uiTree(Context context, long timeoutMs) {
        Object response = withTimeout(
                () -> agentClient.uiTree(context.agentBaseUrl(), context.deviceSerial()), timeoutMs);
        if (response == null) {
            return new DeviceUiTree(null, null, null, List.of());
        }
        try {
            if (response instanceof String json) {
                return objectMapper.readValue(json, DeviceUiTree.class);
            }
            return objectMapper.convertValue(response, DeviceUiTree.class);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new StepActionException("解析 UI 树失败: " + e.getMessage(), e);
        }
    }

    private String captureScreenshot(Context context, long stepTimeoutMs) {
        try {
            long timeoutMs = Math.max(500L, Math.min(MAX_SCREENSHOT_TIMEOUT_MS, stepTimeoutMs));
            byte[] png = withTimeout(
                    () -> agentClient.screenshot(context.agentBaseUrl(), context.deviceSerial()), timeoutMs);
            if (png == null || png.length == 0 || png.length > MAX_SCREENSHOT_BYTES) {
                return null;
            }
            return Base64.getEncoder().encodeToString(png);
        } catch (RuntimeException e) {
            log.debug("步骤截图失败（忽略） taskId={}: {}", context.taskId(), e.getMessage());
            return null;
        }
    }

    private <T> T withTimeout(Callable<T> call, long timeoutMs) {
        FutureTask<T> future = new FutureTask<>(call);
        Thread worker = new Thread(future, "step-agent-call");
        worker.setDaemon(true);
        worker.start();
        try {
            return future.get(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new StepTimeoutException("操作超过 " + timeoutMs + "ms 未完成");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new StepActionException(cause == null ? e.getMessage() : cause.getMessage(), cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new StepCancelledException("步骤执行被中断", e);
        }
    }

    // ------------------------------------------------------------- helpers

    private void recordStep(Context context, int seq, String status, String decision, String action,
                            long elapsedMs, String screenshotBase64) {
        try {
            stepService.upsert(context.taskId(), new StepCallbackRequest(seq, status, decision, action,
                    elapsedMs, screenshotBase64, screenshotBase64 == null ? null : "image/png"));
        } catch (RuntimeException e) {
            log.warn("记录步骤结果失败 taskId={} seq={}: {}", context.taskId(), seq, e.getMessage());
        }
    }

    private void sleep(Context context, long millis) {
        long end = System.currentTimeMillis() + Math.max(0, millis);
        while (true) {
            if (isCancelled(context)) {
                throw new StepCancelledException("任务已取消", null);
            }
            long remaining = end - System.currentTimeMillis();
            if (remaining <= 0) {
                return;
            }
            try {
                Thread.sleep(Math.min(remaining, 100L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StepCancelledException("WAIT 被中断", e);
            }
        }
    }

    private boolean isCancelled(Context context) {
        try {
            return context.cancelled() != null && context.cancelled().getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private List<IndexedStep> enabledSteps(List<StepSpec> specs) {
        List<IndexedStep> result = new ArrayList<>();
        int index = 0;
        for (StepSpec spec : specs) {
            index++;
            if (spec != null && spec.enabledOrDefault()) {
                result.add(new IndexedStep(spec.seqOr(index), spec));
            }
        }
        result.sort(Comparator.comparingInt(IndexedStep::seq));
        return result;
    }

    private Map<String, Object> actionBody(String action) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        return body;
    }

    private Map<String, Object> target(String selectorType, String selector) {
        Map<String, Object> target = new LinkedHashMap<>();
        if ("ID".equals(selectorType)) {
            target.put("id", selector);
        } else {
            target.put("text", selector);
        }
        return target;
    }

    private Map<String, Object> point(int x, int y) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("x", x);
        point.put("y", y);
        return point;
    }

    private int[] parsePoints(String raw, int expected, String label) {
        if (raw == null || raw.isBlank()) {
            throw new StepActionException(label + " 坐标为空");
        }
        String[] parts = raw.split(",");
        if (parts.length != expected) {
            throw new StepActionException(label + " 需要 " + expected + " 个坐标值: " + raw);
        }
        int[] values = new int[expected];
        for (int i = 0; i < expected; i++) {
            values[i] = (int) parseLong(parts[i].trim(), label);
        }
        return values;
    }

    private long parseLong(String raw, String label) {
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0) {
                throw new StepActionException(label + " 必须是非负整数: " + raw);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new StepActionException(label + " 必须是整数: " + raw);
        }
    }

    private boolean responseOk(Object response) {
        if (response instanceof Map<?, ?> map) {
            Object ok = map.get("ok");
            if (ok instanceof Boolean value) {
                return value;
            }
            if (ok instanceof String value) {
                return Boolean.parseBoolean(value);
            }
        }
        return true;
    }

    private String responseMessage(Object response, String fallback) {
        if (response instanceof Map<?, ?> map) {
            Object message = map.get("message");
            if (message != null && !String.valueOf(message).isBlank()) {
                return truncate(String.valueOf(message), 2000);
            }
        }
        if (response instanceof String value && !value.isBlank()) {
            return truncate(value, 2000);
        }
        return fallback;
    }

    private String defaultSelectorType(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.toUpperCase(Locale.ROOT);
    }

    private boolean equalsOrContains(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return actual.equals(expected) || actual.contains(expected);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private String truncate(String value, int max) {
        return value == null ? null : value.length() <= max ? value : value.substring(0, max);
    }

    /** Everything the runner needs; supplied by {@link TaskService} right before the async hop. */
    public record Context(String taskId, String agentBaseUrl, String deviceSerial, String caseTitle,
                          List<StepSpec> steps, int timeoutSec, int stepTimeoutSec, Instant startedAt,
                          BooleanSupplier cancelled) {

        public Context {
            steps = steps == null ? List.of() : List.copyOf(steps);
            cancelled = cancelled == null ? () -> false : cancelled;
            startedAt = startedAt == null ? Instant.now() : startedAt;
        }
    }

    public record StepRunResult(TaskStatus status, FailureCategory failureReason, String summary) {

        public static StepRunResult success(String summary) {
            return new StepRunResult(TaskStatus.SUCCESS, null, summary);
        }

        public static StepRunResult failed(FailureCategory category, String summary) {
            return new StepRunResult(TaskStatus.FAILED,
                    category == null ? FailureCategory.ACTION_FAILED : category, summary);
        }

        public static StepRunResult timeout(String summary) {
            return new StepRunResult(TaskStatus.TIMEOUT, FailureCategory.TIMEOUT, summary);
        }

        public static StepRunResult cancelled(String summary) {
            return new StepRunResult(TaskStatus.CANCELLED, FailureCategory.MANUAL_INTERRUPT, summary);
        }

        public boolean success() {
            return status == TaskStatus.SUCCESS;
        }
    }

    private record IndexedStep(int seq, StepSpec step) {
    }

    private record StepOutcome(boolean ok, FailureCategory category, String decision, String action) {

        static StepOutcome ok(String decision, String action) {
            return new StepOutcome(true, null, decision, action);
        }

        static StepOutcome assertionFailed(String decision, String action) {
            return new StepOutcome(false, FailureCategory.ASSERTION_FAILED, decision, action);
        }

        static StepOutcome actionFailed(String decision, String action) {
            return new StepOutcome(false, FailureCategory.ACTION_FAILED, decision, action);
        }
    }

    private static class StepTimeoutException extends RuntimeException {

        StepTimeoutException(String message) {
            super(message);
        }
    }

    private static class StepActionException extends RuntimeException {

        StepActionException(String message) {
            super(message);
        }

        StepActionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class StepCancelledException extends RuntimeException {

        StepCancelledException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
