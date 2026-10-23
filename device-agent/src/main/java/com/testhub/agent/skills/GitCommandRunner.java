package com.testhub.agent.skills;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Abstraction over the {@code git} binary so the Skills cache can be tested
 * without git or network access.
 */
public interface GitCommandRunner {

    /**
     * Run git with the given arguments in {@code workDir}.
     *
     * @return process exit code (124 means timeout)
     */
    int run(Path workDir, List<String> args, Duration timeout) throws IOException, InterruptedException;
}
