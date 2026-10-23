package com.testhub.agent.skills;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the system {@code git} binary. Output is deliberately drained and never
 * logged: remote URLs may embed credentials.
 */
@Component
public class ProcessGitCommandRunner implements GitCommandRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessGitCommandRunner.class);

    @Override
    public int run(Path workDir, List<String> args, Duration timeout)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(args.size() + 1);
        command.add("git");
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException("git binary is not available", e);
        }
        Thread drain = new Thread(() -> {
            try (OutputStream sink = OutputStream.nullOutputStream()) {
                process.getInputStream().transferTo(sink);
            } catch (IOException ignored) {
                // process output is intentionally discarded
            }
        }, "agent-git-output");
        drain.setDaemon(true);
        drain.start();

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            log.warn("git command timed out after {}s", timeout.toSeconds());
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            return 124;
        }
        drain.join(2000);
        return process.exitValue();
    }
}
