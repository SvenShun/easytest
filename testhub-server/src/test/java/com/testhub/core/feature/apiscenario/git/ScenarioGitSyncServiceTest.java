package com.testhub.core.feature.apiscenario.git;

import com.testhub.core.feature.apiscenario.ApiScenario;
import com.testhub.core.feature.apiscenario.ApiScenarioRepository;
import com.testhub.core.feature.apiscenario.ScenarioGraphAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScenarioGitSyncServiceTest {

    @TempDir
    Path temp;

    @Test
    void syncsYamlFromLocalGitRepository() throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo.resolve("scenarios"));
        Files.writeString(repo.resolve("scenarios/login.yaml"), """
                name: Git login
                variables: {username: tester}
                steps:
                  - name: login
                    type: http
                    method: POST
                    path: /login
                    body: {username: "${username}"}
                """);
        git(repo, "init", "-q");
        git(repo, "config", "user.email", "test@example.com");
        git(repo, "config", "user.name", "Test");
        git(repo, "add", ".");
        git(repo, "commit", "-q", "-m", "init");

        ScenarioGitConfigRepository configs = mock(ScenarioGitConfigRepository.class);
        ApiScenarioRepository scenarios = mock(ApiScenarioRepository.class);
        ScenarioGitConfig config = new ScenarioGitConfig("default");
        config.setRepoUrl(repo.toString());
        config.setBranchName("main");
        config.setYamlDirectory("scenarios");
        config.setEnabled(true);
        when(configs.findById("default")).thenReturn(Optional.of(config));
        when(configs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(scenarios.findBySourcePath(anyString())).thenReturn(Optional.empty());
        when(scenarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ScenarioGitSyncService service = new ScenarioGitSyncService(configs, scenarios,
                new ScenarioGraphAnalyzer());
        var result = service.sync("tester");

        assertThat(result.commit()).hasSize(40);
        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        verify(scenarios).save(any(ApiScenario.class));
        assertThat(config.getLastCommit()).isEqualTo(result.commit());
    }

    private void git(Path repo, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }
}
