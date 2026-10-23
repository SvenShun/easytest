package com.testhub.agent.executor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.testhub.agent.config.AgentProperties;
import com.testhub.agent.dispatch.AgentTask;
import com.testhub.agent.dispatch.CallbackClient;
import com.testhub.agent.dispatch.Payloads;
import com.testhub.agent.dispatch.TaskDispatchRequest;
import com.testhub.agent.dispatch.TaskReporter;
import com.testhub.agent.dispatch.TaskStatus;
import com.testhub.agent.http.HttpJsonClient;

/**
 * Traditional script executor: download zip bundle, extract safely, run
 * {@code sh -c entryCommand}, stream logs, parse JUnit XML, upload artifacts and
 * report the terminal result.
 */
@Component
public class ScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScriptExecutor.class);
    private static final Duration BUNDLE_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_JUNIT_FILES = 50;

    private final AgentProperties properties;
    private final HttpJsonClient http;
    private final CallbackClient callbacks;
    private final JunitXmlParser junitParser;
    private final ArtifactCollector artifactCollector;
    private final TaskReporter taskReporter;

    public ScriptExecutor(AgentProperties properties, HttpJsonClient http, CallbackClient callbacks,
            JunitXmlParser junitParser, ArtifactCollector artifactCollector,
            TaskReporter taskReporter) {
        this.properties = properties;
        this.http = http;
        this.callbacks = callbacks;
        this.junitParser = junitParser;
        this.artifactCollector = artifactCollector;
        this.taskReporter = taskReporter;
    }

    public void execute(AgentTask task, TaskDispatchRequest request) {
        long startedAt = System.currentTimeMillis();
        String token = request.callbackToken(properties.getToken());
        Path taskDir = properties.dataPath().resolve("tasks").resolve(safeSegment(task.getTaskId()));
        Path bundleDir = taskDir.resolve("bundle");
        Path outputDir = taskDir.resolve("output");
        Path logFile = taskDir.resolve("task.log");

        Outcome outcome = null;
        try {
            Files.createDirectories(bundleDir);
            Files.createDirectories(outputDir);
            try (LogReporter logs = new LogReporter(callbacks, logUrl(request), token, logFile)) {
                try {
                    outcome = run(task, request, startedAt, logs, taskDir, bundleDir, outputDir);
                } catch (Exception e) {
                    log.error("script task {} failed unexpectedly", task.getTaskId(), e);
                    logs.error("script execution failed: " + rootMessage(e));
                    outcome = Outcome.failed(TaskStatus.FAILED, "SCRIPT_FAILED",
                            "script execution failed: " + rootMessage(e));
                }
                logs.info("final status: " + outcome.status() + " - " + outcome.summary());
            }
        } catch (IOException e) {
            log.error("cannot prepare task workspace for {}", task.getTaskId(), e);
            outcome = Outcome.failed(TaskStatus.FAILED, "SCRIPT_FAILED",
                    "cannot prepare task workspace: " + rootMessage(e));
        }

        postSteps(request, token, outcome);
        List<Payloads.Artifact> artifacts = artifactCollector.collect(outputDir, outcome.junitFiles(), logFile);
        taskReporter.report(task, request, outcome.status(), outcome.reasonCategory(),
                outcome.summary(), artifacts);
    }

    // ------------------------------------------------------------------
    // execution
    // ------------------------------------------------------------------

    private Outcome run(AgentTask task, TaskDispatchRequest request, long startedAt,
            LogReporter logs, Path taskDir, Path bundleDir, Path outputDir) {
        String serial = request.deviceSerial();
        logs.info("task " + task.getTaskId() + " accepted (executor=SCRIPT, device="
                + (serial == null || serial.isBlank() ? "none" : serial) + ")");

        if (task.isCancelRequested()) {
            return Outcome.cancelled("task cancelled before bundle download");
        }

        TaskDispatchRequest.Script script = request.script();
        if (script == null || script.bundleUrl() == null || script.bundleUrl().isBlank()) {
            logs.error("script bundleUrl is missing");
            return Outcome.failed(TaskStatus.FAILED, "EXECUTOR_UNAVAILABLE",
                    "script bundleUrl is missing");
        }

        Path zipFile = taskDir.resolve("bundle.zip");
        try {
            logs.info("downloading script bundle");
            byte[] zip = http.getBytes(script.bundleUrl(), BUNDLE_TIMEOUT);
            Files.write(zipFile, zip);
            logs.info("bundle downloaded: " + zip.length + " bytes");
        } catch (IOException e) {
            logs.error("bundle download failed: " + rootMessage(e));
            return Outcome.failed(TaskStatus.FAILED, "SCRIPT_FAILED",
                    "bundle download failed: " + rootMessage(e));
        } catch (RuntimeException e) {
            logs.error("bundle download failed: " + rootMessage(e));
            return Outcome.failed(TaskStatus.FAILED, "SCRIPT_FAILED",
                    "bundle download failed: " + rootMessage(e));
        }

        if (task.isCancelRequested()) {
            return Outcome.cancelled("task cancelled before bundle extraction");
        }

        try {
            ZipExtractor.extract(zipFile, bundleDir);
            logs.info("bundle extracted to " + bundleDir);
        } catch (ZipExtractor.ZipSlipException e) {
            logs.error("unsafe bundle rejected: " + e.getMessage());
            return Outcome.failed(TaskStatus.FAILED, "SCRIPT_FAILED",
                    "unsafe bundle: " + e.getMessage());
        } catch (IOException e) {
            logs.error("bundle extraction failed: " + rootMessage(e));
            return Outcome.failed(TaskStatus.FAILED, "SCRIPT_FAILED",
                    "bundle extraction failed: " + rootMessage(e));
        }

        if (task.isCancelRequested()) {
            return Outcome.cancelled("task cancelled before execution");
        }

        String entryCommand = script.safeEntryCommand();
        logs.info("executing command: sh -c \"" + entryCommand + "\"");
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", entryCommand);
            builder.directory(bundleDir.toFile());
            builder.redirectErrorStream(true);
            builder.environment().put("TESTHUB_TASK_ID", task.getTaskId());
            builder.environment().put("TESTHUB_DEVICE_SERIAL", serial == null ? "" : serial);
            builder.environment().put("TESTHUB_OUTPUT_DIR", outputDir.toString());
            builder.environment().put("TESTHUB_EXECUTOR", "SCRIPT");
            process = builder.start();
        } catch (IOException e) {
            logs.error("cannot start entry command: " + rootMessage(e));
            return Outcome.failed(TaskStatus.FAILED, "SCRIPT_FAILED",
                    "cannot start entry command: " + rootMessage(e));
        }

        task.setProcess(process);
        Thread reader = startLogReader(process, logs);
        long deadline = startedAt + request.totalSec() * 1000L;
        long waitMs = Math.max(1L, deadline - System.currentTimeMillis());

        boolean finished = false;
        boolean interrupted = false;
        try {
            finished = process.waitFor(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            interrupted = true;
            AgentTask.destroyProcessTree(process);
        }

        boolean timedOut = !finished && !interrupted;
        if (timedOut) {
            logs.error("task timed out after " + request.totalSec() + "s, destroying process tree");
            AgentTask.destroyProcessTree(process);
        }
        joinQuietly(reader, 5000);
        boolean cancelRequested = task.isCancelRequested();
        task.setProcess(null);

        if (cancelRequested) {
            logs.warn("task cancel requested, process terminated");
            return Outcome.cancelled("task cancelled by request");
        }
        if (interrupted) {
            return Outcome.failed(TaskStatus.FAILED, "MANUAL_INTERRUPT",
                    "script execution interrupted");
        }
        if (timedOut) {
            return Outcome.timedOut("script timed out after " + request.totalSec() + "s");
        }

        int exitCode;
        try {
            exitCode = process.exitValue();
        } catch (IllegalThreadStateException e) {
            exitCode = -1;
        }
        logs.info("process exited with code " + exitCode);

        long elapsedMs = System.currentTimeMillis() - startedAt;
        List<Path> junitFiles = findJunitFiles(outputDir);
        if (junitFiles.isEmpty()) {
            junitFiles = findJunitFiles(bundleDir);
        }
        List<JunitXmlParser.TestCase> cases = junitParser.parseFiles(junitFiles);

        List<Payloads.Step> steps = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        int seq = 1;
        if (cases.isEmpty()) {
            logs.info("no JUnit XML found; reporting a single exit-code step");
            steps.add(new Payloads.Step(seq, exitCode == 0 ? "OK" : "FAIL",
                    entryCommand, "run", elapsedMs, null, null));
        } else {
            logs.info("parsed " + cases.size() + " testcase(s) from " + junitFiles.size() + " XML file(s)");
            for (JunitXmlParser.TestCase testCase : cases) {
                steps.add(new Payloads.Step(seq++, testCase.status(), testCase.decision(),
                        "assert", testCase.elapsedMs(), null, null));
                if (testCase.failed()) {
                    failedNames.add(testCase.decision());
                }
            }
        }

        if (exitCode == 0 && failedNames.isEmpty()) {
            String summary = cases.isEmpty()
                    ? "script exited with code 0"
                    : "script succeeded: " + cases.size() + " test case(s) passed";
            logs.info(summary);
            return new Outcome(TaskStatus.SUCCESS, null, summary, steps, junitFiles);
        }

        String summary;
        if (failedNames.isEmpty()) {
            summary = "script exited with code " + exitCode;
        } else {
            String names = String.join(", ", failedNames.subList(0, Math.min(failedNames.size(), 20)));
            summary = "script failed: " + failedNames.size() + " of " + cases.size()
                    + " test case(s) failed: " + names;
        }
        logs.error("task failed: " + summary);
        return Outcome.failed(TaskStatus.FAILED, "SCRIPT_FAILED", summary, steps, junitFiles);
    }

    private Thread startLogReader(Process process, LogReporter logs) {
        Thread reader = new Thread(() -> {
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    logs.info(line);
                }
            } catch (IOException e) {
                logs.warn("cannot read process output: " + rootMessage(e));
            }
        }, "agent-script-stdout");
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    // ------------------------------------------------------------------
    // callbacks / artifacts
    // ------------------------------------------------------------------

    private void postSteps(TaskDispatchRequest request, String token, Outcome outcome) {
        if (request.callbacks() == null || request.callbacks().stepUrl() == null
                || request.callbacks().stepUrl().isBlank()) {
            return;
        }
        for (Payloads.Step step : outcome.steps()) {
            callbacks.postStep(request.callbacks().stepUrl(), token, step);
        }
    }

    private List<Path> findJunitFiles(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, 8)) {
            walk.filter(Files::isRegularFile)
                    .filter(ScriptExecutor::isJunitFile)
                    .sorted(Comparator.naturalOrder())
                    .limit(MAX_JUNIT_FILES)
                    .forEach(result::add);
        } catch (IOException e) {
            log.warn("cannot scan JUnit XML under {}: {}", root, e.getMessage());
        }
        return result;
    }

    private static boolean isJunitFile(Path file) {
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("junit.xml") || name.startsWith("TEST-") && lower.endsWith(".xml");
    }

    private String logUrl(TaskDispatchRequest request) {
        return request.callbacks() == null ? null : request.callbacks().logUrl();
    }

    private static void joinQuietly(Thread thread, long millis) {
        try {
            thread.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String safeSegment(String value) {
        String safe = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
            safe = "_" + safe;
        }
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /** Result of the script execution before artifacts are collected. */
    private record Outcome(
            String status,
            String reasonCategory,
            String summary,
            List<Payloads.Step> steps,
            List<Path> junitFiles) {

        static Outcome failed(String status, String reason, String summary) {
            return new Outcome(status, reason, summary, List.of(), List.of());
        }

        static Outcome failed(String status, String reason, String summary,
                List<Payloads.Step> steps, List<Path> junitFiles) {
            return new Outcome(status, reason, summary, steps, junitFiles);
        }

        static Outcome timedOut(String summary) {
            return new Outcome(TaskStatus.TIMEOUT, "TIMEOUT", summary, List.of(), List.of());
        }

        static Outcome cancelled(String summary) {
            return new Outcome(TaskStatus.CANCELLED, "MANUAL_INTERRUPT", summary, List.of(), List.of());
        }
    }
}
