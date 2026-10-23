package com.testhub.agent.executor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.testhub.agent.dispatch.Payloads;

/**
 * Collects JUnit XML, screenshots and the task log as base64 artifacts.
 * Per-artifact limit 2MB, total limit 8MB (raw content bytes).
 */
@Component
public class ArtifactCollector {

    private static final Logger log = LoggerFactory.getLogger(ArtifactCollector.class);

    static final long MAX_ARTIFACT_BYTES = 2L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024;
    static final int MAX_SCREENSHOTS = 10;

    public List<Payloads.Artifact> collect(Path outputDir, List<Path> junitFiles, Path logFile) {
        List<Payloads.Artifact> artifacts = new ArrayList<>();
        Budget budget = new Budget();

        if (junitFiles != null) {
            for (Path file : junitFiles) {
                addFile(artifacts, budget, file, "JUNIT", relativeName(outputDir, file));
            }
        }
        if (logFile != null && Files.isRegularFile(logFile)) {
            addFile(artifacts, budget, logFile, "LOG", "task.log");
        }
        addScreenshots(artifacts, budget, outputDir);
        return artifacts;
    }

    private void addScreenshots(List<Payloads.Artifact> artifacts, Budget budget, Path outputDir) {
        if (outputDir == null || !Files.isDirectory(outputDir)) {
            return;
        }
        List<Path> screenshots = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(outputDir, 8)) {
            walk.filter(Files::isRegularFile)
                    .filter(this::isImage)
                    .sorted(Comparator.naturalOrder())
                    .limit(200)
                    .forEach(screenshots::add);
        } catch (IOException e) {
            log.warn("cannot scan screenshots under {}: {}", outputDir, e.getMessage());
            return;
        }
        int added = 0;
        for (Path screenshot : screenshots) {
            if (added >= MAX_SCREENSHOTS) {
                break;
            }
            if (addFile(artifacts, budget, screenshot, "SCREENSHOT", relativeName(outputDir, screenshot))) {
                added++;
            }
        }
    }

    private boolean addFile(List<Payloads.Artifact> artifacts, Budget budget, Path file,
            String type, String name) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        try {
            long size = Files.size(file);
            if (size <= 0) {
                return false;
            }
            if (size > MAX_ARTIFACT_BYTES) {
                log.warn("artifact {} is {} bytes (> 2MB), skipped", name, size);
                return false;
            }
            if (!budget.canAdd(size)) {
                log.warn("artifact budget (8MB) exhausted at {}, skipped", name);
                return false;
            }
            byte[] content = Files.readAllBytes(file);
            artifacts.add(new Payloads.Artifact(name, type,
                    Base64.getEncoder().encodeToString(content)));
            budget.add(size);
            return true;
        } catch (IOException e) {
            log.warn("cannot read artifact {}: {}", file, e.getMessage());
            return false;
        }
    }

    private boolean isImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private String relativeName(Path root, Path file) {
        if (root == null) {
            return file.getFileName().toString();
        }
        try {
            Path relative = root.toAbsolutePath().normalize()
                    .relativize(file.toAbsolutePath().normalize());
            return relative.toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.getFileName().toString();
        }
    }

    private static final class Budget {
        private long used;

        boolean canAdd(long size) {
            return used + size <= MAX_TOTAL_BYTES;
        }

        void add(long size) {
            used += size;
        }
    }
}
