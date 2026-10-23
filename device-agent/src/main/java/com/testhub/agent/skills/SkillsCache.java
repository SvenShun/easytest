package com.testhub.agent.skills;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.testhub.agent.config.AgentProperties;

/**
 * Local Skills git cache.
 *
 * <p>Layout: {@code {dataDir}/skills/{sha1(repo)[0..12]}/{commit}}. On a miss the
 * repo is fetched shallowly into a temp directory and copied into the cache.
 * When git fails the newest cached commit for that repo is used ("skills
 * degraded to &lt;commit&gt;"); with no cache the task fails with
 * SKILLS_UNAVAILABLE.</p>
 */
@Component
public class SkillsCache {

    private static final Logger log = LoggerFactory.getLogger(SkillsCache.class);
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_COMMIT_LENGTH = 200;

    private final Path baseDir;
    private final GitCommandRunner git;

    @Autowired
    public SkillsCache(AgentProperties properties, GitCommandRunner git) {
        this(properties.dataPath().resolve("skills"), git);
    }

    SkillsCache(Path baseDir, GitCommandRunner git) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.git = git;
    }

    /**
     * Resolve the Skills directory for a repo/commit.
     *
     * @return cached directory, or {@code null} when commit is blank
     * @throws SkillsUnavailableException when fetch fails and no cache exists
     */
    public synchronized Path resolve(String repo, String commit) {
        if (repo == null || repo.isBlank() || commit == null || commit.isBlank()) {
            return null;
        }
        Path repoDir = baseDir.resolve(repoHash(repo));
        Path target = repoDir.resolve(safeCommit(commit));
        if (Files.isDirectory(target)) {
            log.info("skills cache hit: {} @ {}", maskRepo(repo), commit);
            return target;
        }

        Path fallback = latestCachedCommit(repoDir);
        Path tempDir = null;
        try {
            Path tempRoot = baseDir.resolve("tmp");
            Files.createDirectories(tempRoot);
            tempDir = Files.createTempDirectory(tempRoot, "fetch-");
            runGit(tempDir, "init");
            runGit(tempDir, "remote", "add", "origin", repo);
            runGit(tempDir, "fetch", "--depth", "1", "origin", commit);
            runGit(tempDir, "checkout", "FETCH_HEAD");

            Files.createDirectories(repoDir);
            copyTree(tempDir, target);
            log.info("skills cached: {} @ {} -> {}", maskRepo(repo), commit, target);
            return target;
        } catch (Exception e) {
            log.warn("skills fetch failed for {} @ {}: {}", maskRepo(repo), commit,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            deleteQuietly(target);
            if (fallback != null) {
                log.warn("skills degraded to {} for {}", fallback.getFileName(), maskRepo(repo));
                return fallback;
            }
            throw new SkillsUnavailableException(
                    "skills unavailable for " + maskRepo(repo) + ": " + e.getMessage(), e);
        } finally {
            if (tempDir != null) {
                deleteQuietly(tempDir);
            }
        }
    }

    /** SHA-1 of the repo URL, first 12 hex characters. */
    public static String repoHash(String repo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(repo.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    /** Mask credentials embedded in a git URL before logging. */
    public static String maskRepo(String repo) {
        if (repo == null) {
            return "";
        }
        return repo.replaceAll("(://)[^/@]+@", "$1***@");
    }

    private void runGit(Path workDir, String... args) throws IOException, InterruptedException {
        int exitCode = git.run(workDir, List.of(args), GIT_TIMEOUT);
        if (exitCode != 0) {
            throw new IOException("git " + args[0] + " exited with code " + exitCode);
        }
    }

    private Path latestCachedCommit(Path repoDir) {
        if (!Files.isDirectory(repoDir)) {
            return null;
        }
        try (Stream<Path> entries = Files.list(repoDir)) {
            return entries.filter(Files::isDirectory)
                    .filter(path -> !"tmp".equals(path.getFileName().toString()))
                    .max(Comparator.comparing(this::lastModified))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private java.nio.file.attribute.FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return java.nio.file.attribute.FileTime.fromMillis(0);
        }
    }

    private void copyTree(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> walk = Files.walk(source)) {
            List<Path> entries = walk.filter(path -> !path.equals(source))
                    .filter(path -> !source.relativize(path).startsWith(".git"))
                    .toList();
            for (Path entry : entries) {
                Path relative = source.relativize(entry);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String safeCommit(String commit) {
        String safe = commit.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
            safe = "_" + safe;
        }
        return safe.length() > MAX_COMMIT_LENGTH ? safe.substring(0, MAX_COMMIT_LENGTH) : safe;
    }

    private static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
