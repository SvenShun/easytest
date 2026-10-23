package com.testhub.agent.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillsCacheTest {

    private static final String REPO = "file:///tmp/skills-repo";

    @TempDir
    Path tempDir;

    @Test
    void resolvesAndCachesCommit() {
        FakeGit git = new FakeGit(0);
        Path base = tempDir.resolve("skills");
        SkillsCache cache = new SkillsCache(base, git);

        Path first = cache.resolve(REPO, "abc123");
        Path expected = base.resolve(SkillsCache.repoHash(REPO)).resolve("abc123");
        assertEquals(expected.toAbsolutePath().normalize(), first);
        assertTrue(Files.isDirectory(first));
        assertEquals("# skills", read(first.resolve("SKILLS.md")));
        assertEquals("nested", read(first.resolve("sub/notes.txt")));

        List<List<String>> firstCalls = new ArrayList<>(git.calls);
        assertEquals(4, firstCalls.size());
        assertEquals("init", firstCalls.get(0).get(0));
        assertEquals("remote", firstCalls.get(1).get(0));
        assertEquals("fetch", firstCalls.get(2).get(0));
        assertEquals("checkout", firstCalls.get(3).get(0));
        assertTrue(firstCalls.get(2).contains("--depth"));

        // Cache hit: no further git invocations.
        Path second = cache.resolve(REPO, "abc123");
        assertEquals(first, second);
        assertEquals(4, git.calls.size());
    }

    @Test
    void degradesToNewestCachedCommitWhenFetchFails() {
        Path base = tempDir.resolve("skills");
        FakeGit successGit = new FakeGit(0);
        SkillsCache successCache = new SkillsCache(base, successGit);
        Path cached = successCache.resolve(REPO, "cached001");

        FakeGit failingGit = new FakeGit(1);
        SkillsCache failingCache = new SkillsCache(base, failingGit);
        Path degraded = failingCache.resolve(REPO, "missing002");

        assertEquals(cached.toAbsolutePath().normalize(), degraded.toAbsolutePath().normalize());
        assertEquals("cached001", degraded.getFileName().toString());
        assertTrue(Files.isRegularFile(degraded.resolve("SKILLS.md")));
    }

    @Test
    void failsWhenFetchFailsWithoutCache() {
        SkillsCache cache = new SkillsCache(tempDir.resolve("skills"), new FakeGit(1));
        SkillsUnavailableException error = assertThrows(SkillsUnavailableException.class,
                () -> cache.resolve(REPO, "abc123"));
        assertTrue(error.getMessage().contains("skills unavailable"));
    }

    @Test
    void blankCommitMeansNoSkills() {
        SkillsCache cache = new SkillsCache(tempDir.resolve("skills"), new FakeGit(0));
        assertEquals(null, cache.resolve(REPO, " "));
    }

    @Test
    void masksCredentialsAndBuildsStableHash() {
        String masked = SkillsCache.maskRepo("https://user:secret@example.com/repo.git");
        assertEquals("https://***@example.com/repo.git", masked);
        assertEquals(SkillsCache.repoHash(REPO), SkillsCache.repoHash(REPO));
        assertEquals(12, SkillsCache.repoHash(REPO).length());
    }

    @Test
    void fetchesFromLocalFileRepositoryWhenGitIsAvailable() throws Exception {
        assumeTrue(gitAvailable(), "git binary not available");
        Path repo = tempDir.resolve("local-repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        git(repo, "config", "user.email", "test@example.com");
        git(repo, "config", "user.name", "tester");
        Files.writeString(repo.resolve("SKILLS.md"), "# local skills", StandardCharsets.UTF_8);
        git(repo, "add", ".");
        git(repo, "commit", "-qm", "init");
        String commit = git(repo, "rev-parse", "HEAD").trim();

        SkillsCache cache = new SkillsCache(tempDir.resolve("real-cache"),
                new ProcessGitCommandRunner());
        Path resolved = cache.resolve("file://" + repo.toAbsolutePath(), commit);

        assertTrue(Files.isDirectory(resolved));
        assertEquals("# local skills", read(resolved.resolve("SKILLS.md")));
    }

    private static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version")
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            process.getInputStream().readAllBytes();
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String git(Path directory, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(finished, "git command timed out: " + args[0]);
        assertEquals(0, process.exitValue(), "git " + args[0] + " failed: " + output);
        return output;
    }

    private String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Fake git that writes Skills files on checkout; returns {@code exitCode} otherwise. */
    private static final class FakeGit implements GitCommandRunner {

        private final int exitCode;
        private final List<List<String>> calls = new ArrayList<>();

        private FakeGit(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public int run(Path workDir, List<String> args, Duration timeout) throws IOException {
            calls.add(List.copyOf(args));
            if (exitCode != 0) {
                return exitCode;
            }
            if (!args.isEmpty() && "checkout".equals(args.get(0))) {
                Files.writeString(workDir.resolve("SKILLS.md"), "# skills", StandardCharsets.UTF_8);
                Files.createDirectories(workDir.resolve("sub"));
                Files.writeString(workDir.resolve("sub/notes.txt"), "nested",
                        StandardCharsets.UTF_8);
            }
            return 0;
        }
    }
}
