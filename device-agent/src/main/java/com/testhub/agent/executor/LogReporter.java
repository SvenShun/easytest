package com.testhub.agent.executor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.testhub.agent.dispatch.CallbackClient;
import com.testhub.agent.dispatch.Payloads;

/**
 * Writes task logs to a local file and streams them to ui-execution in batches
 * (at most 20 lines or ~200ms per request).
 */
public class LogReporter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LogReporter.class);

    private final CallbackClient callbacks;
    private final String logUrl;
    private final String token;
    private final BlockingQueue<Payloads.LogLine> queue = new LinkedBlockingQueue<>();
    private final BufferedWriter writer;
    private final Thread sender;
    private volatile boolean finished;

    public LogReporter(CallbackClient callbacks, String logUrl, String token, Path logFile)
            throws IOException {
        this.callbacks = callbacks;
        this.logUrl = logUrl;
        this.token = token;
        if (logFile.getParent() != null) {
            Files.createDirectories(logFile.getParent());
        }
        this.writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        this.sender = new Thread(this::sendLoop, "agent-log-sender");
        this.sender.setDaemon(true);
        this.sender.start();
    }

    public void info(String message) {
        add("INFO", message);
    }

    public void warn(String message) {
        add("WARN", message);
    }

    public void error(String message) {
        add("ERROR", message);
    }

    private void add(String level, String message) {
        String safe = message == null ? "" : message;
        Payloads.LogLine line = new Payloads.LogLine(level, safe);
        queue.add(line);
        try {
            writer.write(Instant.now() + " [" + level + "] " + safe);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.warn("cannot write local task log: {}", e.getMessage());
        }
    }

    private void sendLoop() {
        try {
            while (true) {
                Payloads.LogLine first = queue.poll(200, TimeUnit.MILLISECONDS);
                if (first == null) {
                    if (finished) {
                        break;
                    }
                    continue;
                }
                List<Payloads.LogLine> batch = new ArrayList<>(20);
                batch.add(first);
                queue.drainTo(batch, 19);
                callbacks.postLogs(logUrl, token, batch);
                if (finished && queue.isEmpty()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        finished = true;
        try {
            sender.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        List<Payloads.LogLine> rest = new ArrayList<>();
        queue.drainTo(rest);
        if (!rest.isEmpty()) {
            callbacks.postLogs(logUrl, token, rest);
        }
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            log.warn("cannot close local task log: {}", e.getMessage());
        }
    }
}
